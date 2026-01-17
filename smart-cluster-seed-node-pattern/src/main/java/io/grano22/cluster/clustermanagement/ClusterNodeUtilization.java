package io.grano22.cluster.clustermanagement;

import lombok.Getter;
import lombok.Setter;

public class ClusterNodeUtilization {
    @Setter
    @Getter
    private int jobsInProgress;

    @Getter
    private final int jobTotalCapacity;

    public ClusterNodeUtilization(int jobsInProgress, int jobsTotalCapacity) {
        this.jobsInProgress = jobsInProgress;
        this.jobTotalCapacity = jobsTotalCapacity;
    }

    public ClusterNodeUtilization() {
        this(0, 4);
    }
}
