package org.sample;

import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class NodesMeshManager {
    @Getter
    private final Set<Cluster> clusters = ConcurrentHashMap.newKeySet();
    @Getter
    private final ClusterNode self;

    public static NodesMeshManager initMeshFromConfig(NodeConfig nodeSettings) {
        var selfNode = new ClusterNode(
            nodeSettings.hostname(),
            nodeSettings.communicationPort(),
            nodeSettings.heartbeatPort(),
            ZonedDateTime.now(),
            -1
        );

        return new NodesMeshManager(
            selfNode,
            nodeSettings.clusterSettingsForNode()
                .stream()
                .map(settings -> new Cluster(settings.clusterName(), Set.of(selfNode)))
                .collect(Collectors.toSet())
        );
    }

    public NodesMeshManager(ClusterNode self, Set<Cluster> clusters) {
        this.self = self;
        this.clusters.addAll(clusters);
    }
}
