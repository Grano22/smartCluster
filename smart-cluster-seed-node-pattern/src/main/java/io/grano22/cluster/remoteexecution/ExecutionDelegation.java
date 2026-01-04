package io.grano22.cluster.remoteexecution;

import lombok.NonNull;
import io.grano22.cluster.runtime.ExecutionRuntime;

public record ExecutionDelegation(
     @NonNull String runtimeName,
     @NonNull ExecutionRuntime.Input input
) {
}
