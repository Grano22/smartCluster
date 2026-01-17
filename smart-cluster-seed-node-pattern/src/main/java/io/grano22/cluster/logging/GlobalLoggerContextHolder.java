package io.grano22.cluster.logging;

import lombok.NonNull;
import org.slf4j.spi.LoggingEventBuilder;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class GlobalLoggerContextHolder {
    private static volatile @NonNull LoggerContext context = LoggerContext.create();

    public static synchronized void updateAtomically(@NonNull Function<LoggerContext, LoggerContext> updater) {
        context = updater.apply(context);
    }

    public static synchronized LoggingEventBuilder propagateTo(@NonNull LoggingEventBuilder loggingEventBuilder) {
        for (var entry: context.context().entrySet()) {
            loggingEventBuilder = loggingEventBuilder.addKeyValue(entry.getKey(), entry.getValue());
        }

        return loggingEventBuilder;
    }
}
