package org.sample.remoteexecution;

import lombok.NonNull;
import org.sample.AlivenessCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class RemoteExecutionDelegator {
    private final static Marker contextMarker = MarkerFactory.getMarker("RemoteExecutionDelegator");
    private final static Logger logger = LoggerFactory.getLogger(RemoteExecutionDelegator.class);
    private final JsonMapper mapper = JsonMapper.shared();

    public @NonNull CompletableFuture<RemoteExecutionSummary> delegate(
        @NonNull String hostname,
        int port,
        final @NonNull ExecutionDelegation delegation
    ) {
        return CompletableFuture.supplyAsync(() -> {
            logger.atInfo().log("Starting delegation to {}:{}", hostname, port);

            try (
                Socket socket = new Socket(hostname, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                String rawRequest = mapper.writeValueAsString(delegation);
                writer.println(rawRequest);

                return mapper.readValue(reader, RemoteExecutionSummary.class);
            } catch (Exception e) {
                logger.atError()
                    .addMarker(contextMarker)
                    .setCause(e)
                    .log("Failed to delegate execution to {}:{}", hostname, port)
                ;

                throw new RuntimeException(e);
            }
        }).orTimeout(40, TimeUnit.SECONDS);
    }
}
