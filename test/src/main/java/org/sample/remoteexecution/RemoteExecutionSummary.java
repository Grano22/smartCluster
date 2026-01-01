package org.sample.remoteexecution;

import lombok.Builder;
import lombok.NonNull;
import org.sample.runtime.ExecutionRuntime;

@Builder
public record RemoteExecutionSummary(
     @NonNull ExecutionRuntime.Result result
) {
}
