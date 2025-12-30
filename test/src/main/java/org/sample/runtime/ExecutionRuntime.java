package org.sample.runtime;

import lombok.NonNull;

import java.util.Map;

public interface ExecutionRuntime {
    record Input(@NonNull String command, @NonNull String[] positionalArguments, @NonNull Map<String, String> options) {
        public Input(@NonNull String command, @NonNull String[] positionalArguments) {
            this(command, positionalArguments, Map.of());
        }

        public Input(@NonNull String command) {
            this(command, new String[0], Map.of());
        }

        public Input(@NonNull String command, @NonNull Map<String, String> options) {
            this(command, new String[0], options);
        }
    }
    record Result(int statusCode, @NonNull String output) {}

    @NonNull Result execute(@NonNull final Input input);
    @NonNull String name();
}
