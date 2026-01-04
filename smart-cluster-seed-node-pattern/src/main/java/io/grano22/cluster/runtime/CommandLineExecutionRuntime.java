package io.grano22.cluster.runtime;

import lombok.NonNull;

import java.util.function.Function;

public final class CommandLineExecutionRuntime implements ExecutionRuntime {
    private final @NonNull String handlerName;
    private final @NonNull Function<Input, Result> handler;

    public CommandLineExecutionRuntime(@NonNull String handlerName, @NonNull Function<ExecutionRuntime.Input, ExecutionRuntime.Result> handler) {
        this.handlerName = handlerName;
        this.handler = handler;
    }

    @Override
    public @NonNull Result execute(@NonNull final Input input) {
        try {
            return handler.apply(input);
        } catch (Exception e) {
            return new Result(1, e.getMessage());
        }
    }

    @Override
    public @NonNull String name() {
        return "CLI[" + handlerName + "]";
    }
}
