package org.sample;

import java.util.Set;

public record Cluster(String name, Set<ClusterNode> nodes) {}
