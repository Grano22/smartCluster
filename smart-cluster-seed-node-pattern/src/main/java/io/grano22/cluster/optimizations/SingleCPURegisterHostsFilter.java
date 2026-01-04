package io.grano22.cluster.optimizations;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SingleCPURegisterHostsFilter {
    private final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();
    private final long fastFilter;

    public SingleCPURegisterHostsFilter(Set<String> allowedHosts, String defaultStatus) {
        long filterCalculator = 0L;

        for (String host : allowedHosts) {
            statusMap.put(host, defaultStatus);
            filterCalculator |= (1L << (host.hashCode() & 63));
        }

        this.fastFilter = filterCalculator;
    }

    public String getStatus(String host) {
        long bit = 1L << (host.hashCode() & 63);

        if ((fastFilter & bit) == 0) {
            return null;
        }

        return statusMap.get(host);
    }

    public void update(String host, String newStatus) {
        long bit = 1L << (host.hashCode() & 63);

        if ((fastFilter & bit) == 0) {
            return;
        }

        statusMap.computeIfPresent(host, (_, _) -> newStatus);
    }

    public Set<String> getAllInStatus(String status) {
        return statusMap.entrySet()
             .stream()
             .filter((v) -> status.equals(v.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet())
        ;
    }
}
