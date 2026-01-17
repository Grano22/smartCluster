package io.grano22.cluster.runtime;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.NonNull;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    sealed interface ExecutionResult permits Result, UnionResult {
        int statusCode();
        @NonNull String output();
        @NonNull ZonedDateTime completedAt();
        UnionResult withNextResult(@NonNull Result nextResult);
    }

    @JsonTypeName("simple")
    record Result(int statusCode, @NonNull String output, @NonNull ZonedDateTime completedAt) implements ExecutionResult {
        public Result(int statusCode, @NonNull String output) {
            this(statusCode, output, ZonedDateTime.now());
        }

        public UnionResult withNextResult(@NonNull Result localResult) {
            return new UnionResult(Set.of(this, localResult));
        }
    }

    @JsonTypeName("union")
    record UnionResult(int statusCode, @NonNull String output, @NonNull Set<Result> results, @NonNull ZonedDateTime completedAt) implements ExecutionResult {
        public UnionResult() {
            this(0, "", Collections.emptySet(), ZonedDateTime.now());
        }

        public UnionResult(int statusCode, @NonNull String output, @NonNull Set<Result> results) {
            this(statusCode, output, results, ZonedDateTime.now());
        }

        public UnionResult(@NonNull Set<Result> results) {
            var summaryResult = results
                .stream()
                .map(r -> new AbstractMap.SimpleImmutableEntry<>(r.statusCode, r.output))
                .reduce((previous, next) -> new AbstractMap.SimpleImmutableEntry<>(
                    previous.getKey() != 0 ? (next.getKey() == 0 ? previous.getKey() : next.getKey()) : next.getKey(),
                    previous.getValue() + "\n" + next.getValue()
                ))
                .orElse(new AbstractMap.SimpleImmutableEntry<>(0, ""))
            ;

            this(summaryResult.getKey(), summaryResult.getValue(), results, ZonedDateTime.now());
        }

        public UnionResult withNextResult(@NonNull Result nextResult) {
            return new UnionResult(
                 nextResult.statusCode(),
                 this.output + "\n" + nextResult.output,
                 this.results,
                 ZonedDateTime.now()
            );
        }
    }

    @NonNull Result execute(@NonNull final Input input);
    @NonNull String name();
}
