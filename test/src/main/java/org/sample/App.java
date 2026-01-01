package org.sample;

import org.sample.clustermanagement.NodeConfig;
import org.sample.clustermanagement.NodeConfigLoader;
import org.sample.clustermanagement.NodesMeshManager;
import org.sample.remoteexecution.RemoteExecutionDelegator;
import org.sample.remoteexecution.RemoteExecutionHandlerJob;
import org.sample.runtime.CommandLineExecutionRuntime;
import org.sample.runtime.ExecutionRuntime;
import org.sample.runtime.LanguageExpressionExecutionRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.simple.SimpleLogger;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

public class App {
    static {
        Properties properties = new Properties();
        properties.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "warn");
        properties.setProperty("org.slf4j.simpleLogger.log.org.sample", "info");
        properties.setProperty(SimpleLogger.LOG_FILE_KEY, "System.out");
        properties.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
        properties.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "true");
        properties.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "true");
        System.setProperties(properties);
    }

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        logger.info("Cluster is starting...");
        NodeConfig config = NodeConfigLoader.load(
            Path.of(
                Optional.ofNullable(System.getenv("CONFIG_FILE"))
                    .orElse("config.json")
            )
        );
        JsonMapper jsonMapper = JsonMapper.shared();
        var nodesMeshManager = NodesMeshManager.initMeshFromConfig(config);
        var remoteExecutionDelegator = new RemoteExecutionDelegator();

        var cliHandler = new CommandLineExecutionRuntime("Program", input -> {
            switch (input.command()) {
                case "info" -> {
                    return new ExecutionRuntime.Result(0, jsonMapper.writeValueAsString(nodesMeshManager.getClusters()));
                }
                case "shutdown" -> System.exit(0);
            }

            return new ExecutionRuntime.Result(0, "Invalid command");
        });
        var runtimeHandlers = Set.of(cliHandler, new LanguageExpressionExecutionRuntime());
        var uiCommandHandler = new UICommandHandler(nodesMeshManager, runtimeHandlers, remoteExecutionDelegator);

        var uiJob = new Thread(new UIJob(config.webPort(), uiCommandHandler::handleMessage));
        uiJob.start();

        var heartBeatJob = new HeartbeatJob(nodesMeshManager);
        heartBeatJob.runForDiscoverableNodes();
        heartBeatJob.run();

        var alivenessCollector = new AlivenessCollector(
            config.heartbeatPort(),
            Executors.newVirtualThreadPerTaskExecutor(),
            nodesMeshManager
        );
        alivenessCollector.start();

        var remoteExecutionHandlerJob = new Thread(new RemoteExecutionHandlerJob(config.communicationPort(), runtimeHandlers));
        remoteExecutionHandlerJob.start();

        logger.info("Cluster started, ready to act");

        var scanner = new Scanner(System.in);
        do {
            System.out.print("> ");
            String input = scanner.nextLine();
            var result = cliHandler.execute(new ExecutionRuntime.Input(input));

            System.out.println("Status Code: " + result.statusCode());
            System.out.println("\nMessage: \n" + result.output());
        } while (true);
    }
}
