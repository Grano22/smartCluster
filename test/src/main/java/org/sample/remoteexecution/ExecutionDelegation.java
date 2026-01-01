package org.sample.remoteexecution;

import lombok.NonNull;
import org.sample.runtime.ExecutionRuntime;

public record ExecutionDelegation(
     @NonNull String runtimeName,
     @NonNull ExecutionRuntime.Input input
) {
}
