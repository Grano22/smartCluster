package io.grano22.cluster;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.grano22.cluster.logging.ConcurrentMemoryLogCollector;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiConsumer;

import lombok.Builder;
import lombok.NonNull;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

@ServerEndpoint("/view/updates")
public final class UISyncEndpoint {
    @Builder
    private record UILogMessage(
        String data,
        String type,
        ZonedDateTime requestedAt,
        ZonedDateTime receivedAt,
        ZonedDateTime processedAt
    ) {
    }

    private final static Marker uiSyncEndpointServiceTag = MarkerFactory.getMarker("UI-Sync");
    private final static Logger logger = LoggerFactory.getLogger(UISyncEndpoint.class);

    private final Map<String, Deque<UILogMessage>> retryDeque = new ConcurrentHashMap<>();

    private final JsonMapper mapper = JsonMapper.shared();

    private record UIClient(
        @NonNull String sessionId,
        @NonNull Session session,
        @NonNull ZonedDateTime connectedAt
    ) {}
    private final ConcurrentHashMap<String, UIClient> clients = new ConcurrentHashMap<>();

    private final @NonNull BiConsumer<String, Session> messageHandler;

    public UISyncEndpoint(@NonNull final BiConsumer<String, Session> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.info("Opened a session: {}", session);
        clients.compute(session.getId(), (_, _) -> new UIClient(session.getId(), session, ZonedDateTime.now()));

        // TODO: Add lazy loading for on open old messages
        for (var log: ConcurrentMemoryLogCollector.getSnapshot()) {
            var command = UILogMessage.builder()
                .type("log_message")
                .data(log.getFormattedMessage())
                .receivedAt(null)
                .processedAt(ZonedDateTime.now(ZoneId.of("UTC")))
                .requestedAt(null)
                .build()
            ;

            try {
                session.getBasicRemote().sendText(mapper.writeValueAsString(command));
            } catch (Exception e) {
                var newQue = retryDeque.compute(session.getId(), (_, _) -> new LinkedBlockingDeque<>());
                newQue.add(command);
            }
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        logger.debug("Received message from the client: {}", message);

        // TODO: Is it thread safe?
        var clientRetryQue = retryDeque.getOrDefault(session.getId(), new LinkedBlockingDeque<>());
        UILogMessage nextLog;
        while ((nextLog = clientRetryQue.poll()) != null) {
            try {
                session.getBasicRemote().sendText(mapper.writeValueAsString(nextLog));
            } catch (Exception e) {
                clientRetryQue.add(nextLog);
            }
        }

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
    public void onClose(Session session) {
        clients.remove(session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.error("Error in websocket session: {}", throwable.getMessage());
    }

    public void emitLogMessage(@NonNull String message) {
        System.out.println("Emitting log message: " + message + " to " + clients.size() + " clients");
        try {
            for (var client : clients.values()) {
                var command = UILogMessage.builder()
                     .type("log_message")
                     .data(message)
                     .receivedAt(null)
                     .processedAt(ZonedDateTime.now(ZoneId.of("UTC")))
                     .requestedAt(null)
                     .build()
                ;

                client.session.getBasicRemote().sendText(mapper.writeValueAsString(command));
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());

            logger.atError()
                .setCause(e)
                .addMarker(uiSyncEndpointServiceTag)
                .log("Failed to send log message to clients")
            ;
        }
    }
}
