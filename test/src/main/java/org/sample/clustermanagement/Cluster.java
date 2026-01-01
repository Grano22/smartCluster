package org.sample.clustermanagement;

import java.util.Objects;
import java.util.Set;

public record Cluster(String name, Set<ClusterNode> nodes) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cluster cluster = (Cluster) o;

        return Objects.equals(name, cluster.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
