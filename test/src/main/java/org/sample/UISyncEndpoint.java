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
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import lombok.Builder;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

@ServerEndpoint("/view/updates")
public class UISyncEndpoint {
    private final static Marker uiSyncEndpointServiceTag = MarkerFactory.getMarker("UI-Sync");
    private final static Logger logger = LoggerFactory.getLogger(UISyncEndpoint.class);

    private record UIClient() {}
    private final ConcurrentHashMap<String, UIClient> clients = new ConcurrentHashMap<>();

    private final @NonNull BiConsumer<String, Session> messageHandler;

    public UISyncEndpoint(@NonNull final BiConsumer<String, Session> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.info("Opened a session: {}", session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        logger.debug("Received message from the client: {}", message);

        try {
            messageHandler.accept(message, session);
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
