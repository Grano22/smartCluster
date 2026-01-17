package io.grano22.cluster.remoteexecution;

import lombok.Builder;
import lombok.NonNull;
import io.grano22.cluster.runtime.ExecutionRuntime;

@Builder
public record RemoteExecutionSummary(
     @NonNull ExecutionRuntime.ExecutionResult result
) {
}
