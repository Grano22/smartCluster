package org.sample;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class AlivenessCollector implements Runnable {
    private static final int BUFFER_SIZE = 65535;

    private final static Marker contextMarker = MarkerFactory.getMarker("AlivenessCollector");
    private final static Logger logger = LoggerFactory.getLogger(AlivenessCollector.class);

    private final JsonMapper mapper = JsonMapper.shared();

    private final @NonNull DatagramSocket socket;
    private final @NonNull ExecutorService executor;
    private final @NonNull NodesMeshManager meshManager;
    private volatile boolean running = true;

    public AlivenessCollector(
         int port,
         final @NonNull ExecutorService executorService,
         final @NonNull NodesMeshManager meshManager
    ) throws SocketException {
        this.socket = new DatagramSocket(port);
        this.executor = executorService;
        this.meshManager = meshManager;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                socket.receive(packet);

                ByteArrayInputStream bis = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                HeartbeatJob.Heartbeat heartbeat = mapper.readValue(bis, HeartbeatJob.Heartbeat.class);

                Set<Cluster> commonClusters = new HashSet<>(heartbeat.clusters());
                commonClusters.retainAll(meshManager.getClusters());

                Set<Cluster> clustersMissing = new HashSet<>(heartbeat.clusters());
                clustersMissing.removeAll(commonClusters);

                for (var cluster: commonClusters) {
                    for (var node: cluster.nodes()) {
                        if (meshManager.hasInCluster(cluster, node)) {
                            continue;
                        }

                        meshManager.addNodeToCluster(cluster, node);
                    }
                }

                meshManager.addClusters(clustersMissing);

                packet.setLength(buffer.length);
            } catch (IOException e) {
                logger.atError()
                    .addMarker(contextMarker)
                    .setCause(e)
                    .log("Failed to collect heartbeat");
            }
        }
    }

    public void start() {
        running = true;
        executor.submit(this);
    }

    public void stop() {
        running = false;
        if (!socket.isClosed()) {
            socket.close();
        }
        logger.info("UDP heartbeat Server stopped.");
    }
}
