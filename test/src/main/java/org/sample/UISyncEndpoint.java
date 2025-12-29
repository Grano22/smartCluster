package org.sample;

import com.fasterxml.jackson.annotation.*;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

@ServerEndpoint("/view/updates")
public class UISyncEndpoint {
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    private sealed interface UISyncCommand {
        @JsonTypeName("query_cluster_details")
        record QueryClusterDetails(ZonedDateTime requestedAt) implements UISyncCommand {}

        ZonedDateTime requestedAt();
    }

    private sealed interface UISyncResponseData {
        record ClustersInfo(Set<Cluster> clusters) implements UISyncResponseData {}
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

    private final static Marker uiSyncEndpointServiceTag = MarkerFactory.getMarker("UI-Sync");
    private final static Logger logger = LoggerFactory.getLogger(UISyncEndpoint.class);
    private final JsonMapper mapper = JsonMapper.builder()
        .build();

    private record UIClient() {}
    private final ConcurrentHashMap<String, UIClient> clients = new ConcurrentHashMap<>();

    private final NodesMeshManager meshManager;

    public UISyncEndpoint(final NodesMeshManager meshManager) {
        this.meshManager = meshManager;
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.info("Opened a session: {}", session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        ZonedDateTime receivedAt = ZonedDateTime.now(ZoneId.of("UTC"));
        logger.debug("Received message from the client: {}", message);

        try {
            UISyncCommand command = mapper.readValue(message, UISyncCommand.class);

            switch (command) {
                case UISyncCommand.QueryClusterDetails clusterDetailsUISyncCommand -> {
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
                    .addMarker(uiSyncEndpointServiceTag)
                    .log("Client tried to perform unsupported operation {}", message)
                ;
            }
        } catch (Exception exception) {
            logger.atError()
                .setCause(exception)
                .addMarker(uiSyncEndpointServiceTag)
                .log("Failed to process websocket message")
            ;
        }
    }

    @OnClose
    public void onClose(Session session) {}

    @OnError
    public void onError(Session session, Throwable throwable) {}
}
