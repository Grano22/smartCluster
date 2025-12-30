package org.sample;

import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.NonNull;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.function.BiConsumer;

public final class UIJob implements Runnable {
    private static final Marker uiJobServiceMarker = MarkerFactory.getMarker("UI job");
    private static final Logger logger = LoggerFactory.getLogger(UIJob.class);

    private final @NonNull Server server;
    private final @NonNull BiConsumer<String, Session> messageHandler;

    public UIJob(int webPort, @NonNull final BiConsumer<String, Session> messageHandler) {
        server = new Server(webPort);
        this.messageHandler = messageHandler;
    }

    public void run() {
        try {
            logger.info("Starting UIJob Server...");
            ServletContextHandler handler = new ServletContextHandler(
                ServletContextHandler.SESSIONS
            );
            handler.setContextPath("/");
            server.setHandler(handler);

            handler.addServlet(UIServeEndpoint.class, "/*");

            var websocketConfig = ServerEndpointConfig.Builder
                .create(UISyncEndpoint.class, "/view/updates")
                .configurator(new ServerEndpointConfig.Configurator() {
                    @Override
                    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                        if (UISyncEndpoint.class.isAssignableFrom(endpointClass)) {
                            var uiSyncEndpoint = new UISyncEndpoint(messageHandler);

                            return endpointClass.cast(uiSyncEndpoint);
                        }

                        return super.getEndpointInstance(endpointClass);
                    }
                })
                 .build()
             ;

            JakartaWebSocketServletContainerInitializer.configure(
                handler,
                (ctx, ws) -> {
                    ws.addEndpoint(websocketConfig);
                }
            );

            server.start();
            server.join();
        } catch (Exception exception) {
            logger.atError()
                .addMarker(uiJobServiceMarker)
                .setCause(exception)
                .log("Failed on UI Job")
            ;
        }
    }
}
