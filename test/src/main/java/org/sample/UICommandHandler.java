package org.sample;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.websocket.Session;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.sample.runtime.ExecutionRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

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
        record ExecuteCommand(@NonNull String runtimeName, @NonNull ExecutionRuntime.Input input, ZonedDateTime requestedAt) implements UISyncCommand {}

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

    public UICommandHandler(final NodesMeshManager meshManager, final Set<ExecutionRuntime> runtimes) {
        this.meshManager = meshManager;
        this.runtimes = runtimes;
    }

    @SneakyThrows
    public void handleMessage(String message, Session session) {
        var receivedAt = ZonedDateTime.now(ZoneId.of("UTC"));
        UISyncCommand command = mapper.readValue(message, UISyncCommand.class);

        switch (command) {
            case UISyncCommand.ExecuteCommand executeCommandUISyncCommand -> {
                var runtime = runtimes.stream()
                    .filter(r -> r.name().equals(executeCommandUISyncCommand.runtimeName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown runtime: " + executeCommandUISyncCommand.runtimeName()));

                var result = runtime.execute(executeCommandUISyncCommand.input());
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
}
