package org.sample.clustermanagement;

import lombok.NonNull;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

public record ClusterNode(
    @NonNull String hostname,
    int communicationPort,
    int heartbeatPort,
    @NonNull ZonedDateTime lastHeartbeat,
    int lastTrip,
    @NonNull Set<String> supportedRuntimes
) {
    public ClusterNode {
        supportedRuntimes = Set.copyOf(supportedRuntimes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var cluster = (ClusterNode) o;

        if (communicationPort != cluster.communicationPort) return false;
        if (heartbeatPort != cluster.heartbeatPort) return false;

        return Objects.equals(hostname, cluster.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, communicationPort, heartbeatPort);
    }
}
