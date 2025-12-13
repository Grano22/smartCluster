package org.sample;

import java.time.ZonedDateTime;

public record ClusterNode(
    String hostname,
    int tcpPort,
    int heartbeatPort,
    ZonedDateTime lastHeartbeat,
    int lastTrip
) {
}
