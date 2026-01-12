package io.grano22.cluster.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.grano22.cluster.UIJob;
import lombok.NonNull;
import lombok.Setter;

public class ConcurrentWebLogEmitter extends AppenderBase<ILoggingEvent> {
    // TODO: Ugly solution, find a better way
    @Setter
    private static UIJob uiJob;

    @Override
    protected void append(@NonNull ILoggingEvent iLoggingEvent) {
        if (uiJob != null) {
            uiJob.emitLogMessage(iLoggingEvent.getFormattedMessage());
        }
    }
}
