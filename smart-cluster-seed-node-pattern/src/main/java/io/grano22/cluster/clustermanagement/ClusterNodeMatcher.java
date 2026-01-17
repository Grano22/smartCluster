package io.grano22.cluster.clustermanagement;

import lombok.Builder;
import lombok.NonNull;

@Builder
public record ClusterNodeMatcher(@NonNull String host, Integer webPort, Integer communicationPort, Integer heartbeatPort) {
    public boolean matchAgainst(@NonNull ClusterNode node) {
        boolean matched = node.hostname().equals(host);

        if (webPort != null) {
            matched = matched && node.webPort() == webPort;
        }

        if (communicationPort != null) {
            matched = matched && node.communicationPort() == communicationPort;
        }

        return matched;
    }
}
