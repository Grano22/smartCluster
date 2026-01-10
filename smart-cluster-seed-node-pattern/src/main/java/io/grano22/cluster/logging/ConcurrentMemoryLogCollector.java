package io.grano22.cluster.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentMemoryLogCollector extends AppenderBase<ILoggingEvent> {
    private static final ConcurrentLinkedDeque<ILoggingEvent> CACHE = new ConcurrentLinkedDeque<>();
    private static final AtomicInteger CURRENT_SIZE = new AtomicInteger(0);
    private static final int MAX_SIZE = 5000;

    @Override
    protected void append(final ILoggingEvent iLoggingEvent) {
        iLoggingEvent.prepareForDeferredProcessing();

        CACHE.addLast(iLoggingEvent);
        int size = CURRENT_SIZE.incrementAndGet();

        while (size > MAX_SIZE) {
            CACHE.removeFirst();
            size = CURRENT_SIZE.decrementAndGet();
        }
    }

    public static List<ILoggingEvent> getSnapshot() {
        return new ArrayList<>(CACHE);
    }
}
