package org.sample;

import lombok.NonNull;

import java.time.ZonedDateTime;
import java.util.Set;

public record ClusterNode(
    @NonNull String hostname,
    int tcpPort,
    int heartbeatPort,
    @NonNull ZonedDateTime lastHeartbeat,
    int lastTrip,
    @NonNull Set<String> supportedRuntimes
) {
    public ClusterNode {
        supportedRuntimes = Set.copyOf(supportedRuntimes);
    }
}
