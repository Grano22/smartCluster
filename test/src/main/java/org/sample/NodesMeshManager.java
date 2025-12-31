package org.sample;

import lombok.Getter;
import lombok.NonNull;
import org.sample.optimizations.SingleCPURegisterHostsFilter;
import org.sample.runtime.LanguageExpressionExecutionRuntime;

import java.lang.ref.WeakReference;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class NodesMeshManager {
    @Getter
    private final Set<Cluster> clusters = ConcurrentHashMap.newKeySet();
    @Getter
    private final @NonNull ClusterNode self;
    @Getter
    private final @NonNull Set<String> discoverableNodes;
    private @NonNull SingleCPURegisterHostsFilter discoverableNodesStatus;

    //private final Map<Cluster, Set<ClusterNode>> clusterNodesCache = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<String, WeakReference<Cluster>> clusterNodesCache = new ConcurrentHashMap<>();

    public static NodesMeshManager initMeshFromConfig(NodeConfig nodeSettings) {
        var selfNode = new ClusterNode(
            nodeSettings.hostname(),
            nodeSettings.communicationPort(),
            nodeSettings.heartbeatPort(),
            ZonedDateTime.now(),
            -1,
            Set.of("CLI[Program]", LanguageExpressionExecutionRuntime.NAME)
        );

        return new NodesMeshManager(
            selfNode,
            nodeSettings.clusterSettingsForNode()
                .stream()
                .map(settings -> new Cluster(settings.clusterName(), Set.of(selfNode)))
                .collect(Collectors.toSet()),
            nodeSettings.nodesToDiscover()
        );
    }

    public NodesMeshManager(
        final @NonNull ClusterNode self,
        final @NonNull Set<Cluster> clusters,
        final @NonNull Set<String> discoverableNodes
    ) {
        this.self = self;
        this.clusters.addAll(clusters);

        for (var cluster : clusters) {
            clusterNodesCache.put(cluster.name(), new WeakReference<>(cluster));
        }

        this.discoverableNodes = discoverableNodes;
        this.discoverableNodesStatus = new SingleCPURegisterHostsFilter(discoverableNodes, "UNKNOWN");
    }

    public void addCluster(Cluster cluster) {
        clusters.add(cluster);
        clusterNodesCache.put(cluster.name(), new WeakReference<>(cluster));
    }

    public void addClusters(Set<Cluster> newClusters) {
        clusters.addAll(newClusters);
        clusterNodesCache.putAll(newClusters.stream().collect(Collectors.toMap(Cluster::name, WeakReference::new)));
    }

    public boolean hasInCluster(Cluster cluster, ClusterNode node) {
        WeakReference<Cluster> clusterRef = clusterNodesCache.get(cluster.name());

        if (clusterRef == null) return false;
        Cluster clusterPointer = clusterRef.get();
        if (clusterPointer == null) return false;

        return clusterPointer.nodes().contains(node);
    }

    public void addNodeToCluster(Cluster cluster, ClusterNode newNode) {
        clusterNodesCache.compute(cluster.name(), (id, currentRef) -> {
            Set<ClusterNode> newNodes;
            Cluster oldCluster = null;

            if (currentRef != null && (oldCluster = currentRef.get()) != null) {
                newNodes = new HashSet<>(oldCluster.nodes());
            } else {
                newNodes = new HashSet<>();
            }

            newNodes.add(newNode);
            Cluster newCluster = new Cluster(id, Collections.unmodifiableSet(newNodes));

            if (oldCluster != null) {
                clusters.remove(oldCluster);
            }
            clusters.add(newCluster);

            return new WeakReference<>(newCluster);
        });
    }

    public void setNodeDiscovered(String host) {
        discoverableNodesStatus.update(host, "OK");
    }

    public void setNodeAsNotHealthy(String host) {
        discoverableNodesStatus.update(host, "DOWN");
    }

    public boolean isNodeHealthy(String host) {
        return discoverableNodesStatus.getStatus(host).equals("OK");
    }

    public Set<String> getAllNotDiscoveredNodes() {
        return discoverableNodes.stream().filter(node -> !isNodeHealthy(node)).collect(Collectors.toSet());
    }
}
