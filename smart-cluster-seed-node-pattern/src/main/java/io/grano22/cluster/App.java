package io.grano22.cluster;

import io.grano22.cluster.clustermanagement.NodeConfig;
import io.grano22.cluster.clustermanagement.NodeConfigLoader;
import io.grano22.cluster.clustermanagement.NodesMeshManager;
import io.grano22.cluster.logging.ConcurrentWebLogEmitter;
import io.grano22.cluster.logging.GlobalLoggerContextHolder;
import io.grano22.cluster.remoteexecution.RemoteExecutionDelegator;
import io.grano22.cluster.remoteexecution.RemoteExecutionHandlerJob;
import io.grano22.cluster.runtime.CommandLineExecutionRuntime;
import io.grano22.cluster.runtime.ExecutionRuntime;
import io.grano22.cluster.runtime.LanguageExpressionExecutionRuntime;
import org.apache.tools.ant.types.Commandline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.slf4j.simple.SimpleLogger;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        logger.info("Cluster is starting...");
        NodeConfig config = NodeConfigLoader.load(
            Path.of(
                Optional.ofNullable(System.getenv("CONFIG_FILE"))
                    .orElse("config.json")
            )
        );
        GlobalLoggerContextHolder.updateAtomically(context ->
            context
               .withWebHost(config.hostname() + ":" + config.webPort())
        );

        JsonMapper jsonMapper = JsonMapper.shared();
        var nodesMeshManager = NodesMeshManager.initMeshFromConfig(config);
        var remoteExecutionDelegator = new RemoteExecutionDelegator();

        var heartBeatJob = new HeartbeatJob(nodesMeshManager);
        heartBeatJob.runForDiscoverableNodes();
        heartBeatJob.run();

        var cliHandler = new CommandLineExecutionRuntime("Program", input -> {
            System.out.println(jsonMapper.writeValueAsString(input));

            switch (input.command()) {
                case "info" -> {
                    return new ExecutionRuntime.Result(0, jsonMapper.writeValueAsString(nodesMeshManager.getClusters()));
                }
                case "join" -> {
                    var netAddr = input.positionalArguments()[0];
                    var netPort = Integer.parseInt(input.positionalArguments()[1]);
                    heartBeatJob.runOnce(Set.of(new InetSocketAddress(netAddr, netPort)), true);

                    return new ExecutionRuntime.Result(0, "OK");
                }
                case "shutdown" -> System.exit(0);
            }

            return new ExecutionRuntime.Result(0, "Invalid command");
        });
        var runtimeHandlers = Set.of(cliHandler, new LanguageExpressionExecutionRuntime());
        var uiCommandHandler = new UICommandHandler(nodesMeshManager, runtimeHandlers, remoteExecutionDelegator);

        var uiJob = new UIJob(config.webPort(), uiCommandHandler::handleMessage);
        var uiJobThread = new Thread(uiJob);
        uiJobThread.start();

        ConcurrentWebLogEmitter.setUiJob(uiJob);

        var alivenessCollector = new AlivenessCollector(
            config.heartbeatPort(),
            Executors.newVirtualThreadPerTaskExecutor(),
            nodesMeshManager
        );
        alivenessCollector.start();

        var remoteExecutionHandlerJob = new Thread(
              new RemoteExecutionHandlerJob(
                  config.communicationPort(),
                  runtimeHandlers,
                  nodesMeshManager
              )
        );
        remoteExecutionHandlerJob.start();

        logger.info("Cluster started, ready to act");

        var scanner = new Scanner(System.in);
        do {
            System.out.print("> ");
            String input = scanner.nextLine();
            var localArgs = Commandline.translateCommandline(input);
            var commandName = localArgs[0];
            var positionalArguments = Arrays.stream(localArgs, 1, localArgs.length)
                .filter(arg -> !arg.startsWith("-"))
                .toArray(String[]::new)
            ;
            var namedArguments = Arrays.stream(localArgs, 1, localArgs.length)
                .filter(arg -> arg.startsWith("-"))
                .map(arg -> arg.split("="))
                 .collect(Collectors.toMap(
                     parts -> parts[0],
                     parts -> parts.length > 1 ? parts[1] : "true",
                     (_, replacement) -> replacement
                 ))
            ;

            var result = cliHandler.execute(new ExecutionRuntime.Input(commandName, positionalArguments, namedArguments));

            System.out.println("Status Code: " + result.statusCode());
            System.out.println("\nMessage: \n" + result.output());
        } while (true);
    }
}
