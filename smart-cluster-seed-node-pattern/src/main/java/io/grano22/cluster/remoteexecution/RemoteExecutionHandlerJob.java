package io.grano22.cluster.remoteexecution;

import io.grano22.cluster.clustermanagement.ClusterNodeMatcher;
import io.grano22.cluster.clustermanagement.NodesMeshManager;
import io.grano22.cluster.logging.GlobalLoggerContextHolder;
import lombok.NonNull;
import io.grano22.cluster.runtime.ExecutionRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RemoteExecutionHandlerJob implements Runnable {
    private final static Marker contextMarker = MarkerFactory.getMarker("RemoteExecutionHandlerJob");
    private final static Logger logger = LoggerFactory.getLogger(RemoteExecutionHandlerJob.class);

    private final JsonMapper mapper = JsonMapper.shared();
    private final ServerSocket serverSocket;
    private final Set<ExecutionRuntime> runtimes;
    private final ThreadPoolExecutor executor;
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    private final NodesMeshManager meshManager;

    public RemoteExecutionHandlerJob(
        int communicationPort,
        final @NonNull Set<ExecutionRuntime> runtimes,
        final @NonNull NodesMeshManager meshManager
    ) throws IOException {
        this.serverSocket = new ServerSocket(communicationPort);
        this.runtimes = runtimes;
        var threadFactory = new ConfigurableThreadFactory("delegated_job_", false);
        this.meshManager = meshManager;

        this.executor = new ThreadPoolExecutor(
            3,
            4,
            3,
            TimeUnit.SECONDS,
            queue,
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                        String line;
                        long startTime = System.nanoTime();
                        while ((line = reader.readLine()) != null) {
                            if (line.trim().isEmpty()) continue;

                            var delegatedTask = mapper.readValue(line, ExecutionDelegation.class);
                            var runtime = runtimes.stream()
                                .filter(r -> r.name().equals(delegatedTask.runtimeName()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Unknown runtime: " + delegatedTask.runtimeName()))
                            ;

                            ExecutionRuntime.ExecutionResult result = runtime.execute(delegatedTask.input());
                            for (var i = 0; i < Math.max(0, delegatedTask.repeatTimes()); i++) {
                                var localResult = runtime.execute(delegatedTask.input());
                                result = result.withNextResult(localResult);
                            }

                            var summary = RemoteExecutionSummary.builder()
                                .result(result)
                                .build()
                            ;
                            writer.println(mapper.writeValueAsString(summary));
                            writer.flush();

                            GlobalLoggerContextHolder.propagateTo(logger.atInfo())
                                .addMarker(contextMarker)
                                .addKeyValue("timeTook", System.nanoTime() - startTime)
                                .log("Requested execution finished")
                            ;

                            break;
                        }

                        clientSocket.close();
                    } catch (IOException exception) {
                        logger.atError()
                            .addMarker(contextMarker)
                            .setCause(exception)
                            .log("Failed to read client request")
                        ;
                    } catch (Exception exception) {
                        logger.atError()
                            .addMarker(contextMarker)
                            .setCause(exception)
                            .log("Failed to handle job: " + exception.getMessage())
                        ;
                    }
                });
                meshManager.updateUtilization(executor.getActiveCount());
            } catch (IOException e) {
                logger.atError()
                    .addMarker(contextMarker)
                    .setCause(e)
                    .log("Failed to accept client connection")
                ;
            }
        }
    }

    private boolean checkIfConnectionIsAlive(PrintWriter writer) {
        return !writer.checkError();
    }

    private static final class ConfigurableThreadFactory implements ThreadFactory {
        private final String prefix;
        private final boolean daemon;
        private final AtomicInteger counter = new AtomicInteger(1);

        public ConfigurableThreadFactory(String prefix, boolean daemon) {
            this.prefix = prefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(@NonNull Runnable job) {
            Thread t = new Thread(job, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(daemon);
            t.setPriority(Thread.NORM_PRIORITY);

            return t;
        }
    }
}
