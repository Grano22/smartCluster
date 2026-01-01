package org.sample;

import lombok.NonNull;
import org.sample.clustermanagement.Cluster;
import org.sample.clustermanagement.ClusterNode;
import org.sample.clustermanagement.NodesMeshManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class HeartbeatJob {
    public record Heartbeat(
        @NonNull ClusterNode sender,
        long timestamp,
        Set<Cluster> clusters
    ) {}

    private final static Marker contextMarker = MarkerFactory.getMarker("Heartbeat-Job");
    private final static Logger logger = LoggerFactory.getLogger(HeartbeatJob.class);

    private final @NonNull NodesMeshManager meshManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final JsonMapper mapper = JsonMapper.shared();

    public HeartbeatJob(@NonNull final NodesMeshManager meshManager) {
        this.meshManager = meshManager;
    }

    public void run() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                runForAlreadyKnownNodes();
            } catch (Exception e) {
                logger.atError()
                    .addMarker(contextMarker)
                    .setCause(e)
                    .log("Failed to send heartbeat")
                ;
            }
        }, 0, 3000, TimeUnit.MILLISECONDS);
    }

    public void runOnce(final Set<InetSocketAddress> sendTo, boolean withCluster) {
        byte[] udpBuffer = new byte[1024];
        long timestamp = System.currentTimeMillis();
        var heartbeat = new Heartbeat(
            meshManager.getSelf(),
            timestamp,
            withCluster ? meshManager.getClusters() : null
        );

        byte[] rawData = mapper.writeValueAsBytes(heartbeat);
        System.arraycopy(rawData, 0, udpBuffer, 0, rawData.length);

        for (var nodeAddress: sendTo) {
            DatagramPacket packet = new DatagramPacket(
                udpBuffer,
                udpBuffer.length,
                nodeAddress
            );

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
            } catch(IOException sockedException) {
                logger.atError()
                    .addMarker(contextMarker)
                    .setCause(sockedException)
                    .log("Failed to send heartbeat to {}", nodeAddress)
                ;
            }
        }
    }

    public void runForDiscoverableNodes() {
        runOnce(
             meshManager.getDiscoverableNodes().stream()
                 .map(node -> {
                     var parts = node.split(":");
                     return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                 })
                 .collect(Collectors.toSet()),
                true
        );

        AtomicReference<ScheduledFuture<?>> futureHolder = new AtomicReference<>();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            Set<InetSocketAddress> rest = meshManager.getAllNotDiscoveredNodes().stream()
                .map(node -> {
                    var parts = node.split(":");
                    return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                })
                .collect(Collectors.toSet())
            ;

            if (futureHolder.get() != null && rest.isEmpty()) {
                futureHolder.get().cancel(false);
            }

            runOnce(rest, true);
        }, 5, 5, TimeUnit.SECONDS);
        futureHolder.set(future);
    }

    public void runForAlreadyKnownNodes() {
        Set<ClusterNode> visitedNodes = new HashSet<>();
        for (var cluster: meshManager.getClusters()) {
            for (var node: cluster.nodes()) {
                if (visitedNodes.contains(node) || meshManager.getSelf().equals(node)) {
                    continue;
                }

                visitedNodes.add(node);
            }
        }

        // TODO: Change to withCluster false to save bandwidth
        runOnce(
            visitedNodes.stream()
                .map(node -> new InetSocketAddress(node.hostname(), node.heartbeatPort()))
                .collect(Collectors.toSet()),
            true
        );
    }
}
