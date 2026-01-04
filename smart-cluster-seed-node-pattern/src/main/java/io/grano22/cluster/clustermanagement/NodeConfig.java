package io.grano22.cluster.clustermanagement;

import io.grano22.cluster.NodeSpec;

import java.util.Set;

public record NodeConfig(
    String hostname,
    int webPort,
    int communicationPort,
    int heartbeatPort,
    Set<String> nodesToDiscover,
    Set<ClusterSettingsForNode> clusterSettingsForNode
) {
    public NodeConfig {
        if (!NodeSpec.isHostValid(hostname)) {
            throw new IllegalArgumentException("Invalid hostname passed to the config");
        }

        if (!NodeSpec.isPortValid(webPort)) {
            throw new IllegalArgumentException("Invalid Web Port passed to the config");
        }

        if (!NodeSpec.isPortValid(communicationPort)) {
            throw new IllegalArgumentException("Invalid TCP Port passed to the config");
        }

        if (!NodeSpec.isPortValid(heartbeatPort)) {
            throw new IllegalArgumentException("Invalid Web Port passed to the config");
        }

        nodesToDiscover = Set.copyOf(nodesToDiscover);
        clusterSettingsForNode = Set.copyOf(clusterSettingsForNode);
    }

    public record ClusterSettingsForNode(String clusterName) {}
}
