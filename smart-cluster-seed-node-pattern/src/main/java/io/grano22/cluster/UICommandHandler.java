package io.grano22.cluster;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.websocket.Session;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import io.grano22.cluster.clustermanagement.Cluster;
import io.grano22.cluster.clustermanagement.NodesMeshManager;
import io.grano22.cluster.remoteexecution.ExecutionDelegation;
import io.grano22.cluster.remoteexecution.RemoteExecutionDelegator;
import io.grano22.cluster.remoteexecution.RemoteExecutionSummary;
import io.grano22.cluster.runtime.ExecutionRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class UICommandHandler {
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    private sealed interface UISyncCommand {
        @JsonTypeName("query_cluster_details")
        record QueryClusterDetails(@NonNull ZonedDateTime requestedAt) implements UISyncCommand {}

        @JsonTypeName("execute_command")
        record ExecuteCommand(
            @NonNull String targetHostname,
            int targetPort,
            @NonNull String runtimeName,
            @NonNull ExecutionRuntime.Input input,
            ZonedDateTime requestedAt
        ) implements UISyncCommand {}

        ZonedDateTime requestedAt();
    }

    private sealed interface UISyncResponseData {
        record ClustersInfo(Set<Cluster> clusters) implements UISyncResponseData {}
        record ExecutionResultDetails(ExecutionRuntime.Result result) implements UISyncResponseData {}
    }

    @Builder
    private record UISyncResponse(
        UISyncResponseData data,
        String type,
        ZonedDateTime requestedAt,
        ZonedDateTime receivedAt,
        ZonedDateTime processedAt
    ) {
    }

    private final static Marker contextMarker = MarkerFactory.getMarker("UI-Command-Handler");
    private final static Logger logger = LoggerFactory.getLogger(UICommandHandler.class);

    private final JsonMapper mapper = JsonMapper.builder()
        .build();
    private final Set<ExecutionRuntime> runtimes;
    private final NodesMeshManager meshManager;
    private final RemoteExecutionDelegator delegator;

    public UICommandHandler(
        final @NonNull NodesMeshManager meshManager,
        final @NonNull Set<ExecutionRuntime> runtimes,
        final @NonNull RemoteExecutionDelegator delegator
    ) {
        this.meshManager = meshManager;
        this.runtimes = runtimes;
        this.delegator = delegator;
    }

    @SneakyThrows
    public void handleMessage(String message, Session session) {
        var receivedAt = ZonedDateTime.now(ZoneId.of("UTC"));
        UISyncCommand command = mapper.readValue(message, UISyncCommand.class);

        switch (command) {
            case UISyncCommand.ExecuteCommand executeCommandUISyncCommand -> {
                ExecutionRuntime.Result result = handleExecution(executeCommandUISyncCommand);
                logger.atInfo().log("Execution result: {}", mapper.writeValueAsString(result));

                var response = UISyncResponse.builder()
                    .type("execution_result")
                    .data(new UISyncResponseData.ExecutionResultDetails(result))
                    .receivedAt(receivedAt)
                    .processedAt(ZonedDateTime.now(ZoneId.of("UTC")))
                    .requestedAt(command.requestedAt())
                    .build()
                ;

                session.getBasicRemote().sendText(mapper.writeValueAsString(response));
            }
            case UISyncCommand.QueryClusterDetails _ -> {
                var response = UISyncResponse.builder()
                    .type("cluster_details")
                    .data(new UISyncResponseData.ClustersInfo(meshManager.getClusters()))
                    .receivedAt(receivedAt)
                    .processedAt(ZonedDateTime.now(ZoneId.of("UTC")))
                    .requestedAt(command.requestedAt())
                    .build()
                ;
                session.getBasicRemote().sendText(mapper.writeValueAsString(response));
            }
            default -> logger.atWarn()
                    .addMarker(contextMarker)
                    .log("Client tried to perform unsupported operation {}", message)
            ;
        }
    }

    private @NonNull ExecutionRuntime.Result handleExecution(@NonNull UISyncCommand.ExecuteCommand executeCommandUISyncCommand) {
        if (
            executeCommandUISyncCommand.targetHostname().equals(meshManager.getSelf().hostname()) &&
            executeCommandUISyncCommand.targetPort() == meshManager.getSelf().communicationPort()
        ) {
            return handleLocalExecution(executeCommandUISyncCommand);
        }

        var summary = delegator.delegate(
            executeCommandUISyncCommand.targetHostname,
            executeCommandUISyncCommand.targetPort,
            new ExecutionDelegation(executeCommandUISyncCommand.runtimeName, executeCommandUISyncCommand.input)
        )
            .exceptionally(e -> {
                String reasonMessage = "Failed to delegate execution, unknown reason";

                if (e instanceof TimeoutException) {
                    reasonMessage = "Failed to delegate execution, timeout";
                }

                logger
                    .atError()
                    .addMarker(contextMarker)
                    .setCause(e)
                    .log(reasonMessage)
                ;

                return new RemoteExecutionSummary(
                    new ExecutionRuntime.Result(1, reasonMessage)
                );
            })
            .join()
        ;

        logger.atInfo().log("Execution result: {}", mapper.writeValueAsString(summary.result()));

        return summary.result();
    }

    private @NonNull ExecutionRuntime.Result handleLocalExecution(@NonNull UISyncCommand.ExecuteCommand executeCommandUISyncCommand) {
        var runtime = runtimes.stream()
                .filter(r -> r.name().equals(executeCommandUISyncCommand.runtimeName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown runtime: " + executeCommandUISyncCommand.runtimeName()));


        return runtime.execute(executeCommandUISyncCommand.input());
    }
}
