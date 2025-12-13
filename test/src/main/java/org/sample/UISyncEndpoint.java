package org.sample;

import com.fasterxml.jackson.annotation.*;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

public class UISyncEndpoint {
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    public sealed interface UISyncCommand {
        @JsonTypeName("query_cluster_details")
        record QueryClusterDetails() implements UISyncCommand {}
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
        logger.info("Received: {}", message);

        try {
            UISyncCommand command = mapper.readValue(message, UISyncCommand.class);

            switch (command) {
                case UISyncCommand.QueryClusterDetails clusterDetailsUISyncCommand -> {
                    session.getBasicRemote().sendText("response");
                }
                default -> {
                    logger.atWarn()
                        .log("Client tried to perform unsupported operation {}", message)
                    ;
                }
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
