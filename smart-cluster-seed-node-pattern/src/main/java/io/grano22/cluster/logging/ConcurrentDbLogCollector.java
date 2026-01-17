package io.grano22.cluster.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import lombok.NonNull;
import lombok.Setter;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ConcurrentDbLogCollector extends AppenderBase<ILoggingEvent> {
    private static final Marker contextMarker = MarkerFactory.getMarker("ConcurrentDbLogCollector");
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentDbLogCollector.class);
    private static final BlockingArrayQueue<ILoggingEvent> toBeStored = new BlockingArrayQueue<>(1000);
    private Connection connection = null;
    private JsonMapper mapper = JsonMapper.shared();

    @Setter
    private ConnectionSource connectionSource;

    @Setter
    private int queueSize = 1;

    @Setter
    private int discardingThreshold = 0;

    @Setter
    private boolean includeCallerData = false;

    public void startConsuming() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

        executor.schedule(() -> {
            while (!toBeStored.isEmpty()) {
                var event = Objects.requireNonNull(toBeStored.poll(), "Event cannot be null");
                storeLog(event);
            }
        }, 4000, TimeUnit.MILLISECONDS);

        executor.shutdown();
    }

    @Override
    public void start() {
        super.start();

        try {
            var connectionSource = Objects.requireNonNull(this.connectionSource, "Connection source cannot be null");
            this.connection = DriverManager.getConnection(connectionSource.getUrl(), connectionSource.getUser(), connectionSource.getPassword());
            createDatabase();
            startConsuming();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    protected void append(@NonNull ILoggingEvent iLoggingEvent) {
        iLoggingEvent.prepareForDeferredProcessing();
        toBeStored.offer(iLoggingEvent);
    }

    private void storeLog(@NonNull ILoggingEvent iLoggingEvent) {
        try(var statement = connection.prepareStatement("INSERT INTO log_entries (message, markers, mdc, severity, threadName, errorMessage) VALUES (?, ?, ?, ?, ?, ?)")) {
            var baseException = Optional.ofNullable(iLoggingEvent.getThrowableProxy()).orElse(new ThrowableProxy(new Exception("")));
            statement.setString(1, iLoggingEvent.getFormattedMessage());
            statement.setString(2, mapper.writeValueAsString(Optional.ofNullable(iLoggingEvent.getMarkerList()).orElse(Collections.emptyList())));
            statement.setString(3, mapper.writeValueAsString(iLoggingEvent.getMDCPropertyMap()));
            statement.setString(4, String.valueOf(iLoggingEvent.getLevel().toInt()));
            statement.setString(5, Optional.ofNullable(iLoggingEvent.getThreadName()).orElse("Unknown"));
            statement.setString(6, baseException.getMessage());

            statement.execute();
        } catch (SQLException exception) {
            logger.atError()
                .setCause(exception)
                .addMarker(contextMarker)
                .addKeyValue("sqlState", exception.getSQLState())
                .log("Failed to execute database query due to " + exception.getMessage())
            ;
        }
    }

    private void createDatabase() {
        try(var statement = connection.createStatement()) {
            statement.execute("""
            CREATE TABLE IF NOT EXISTS log_entries (
                id IDENTITY PRIMARY KEY,
                message TEXT,
                markers JSON,
                mdc JSON,
                severity BIGINT,
                threadName TEXT,
                errorMessage TEXT,
                createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            )
            """);

            logger.atInfo().addMarker(contextMarker).log("Database table created");
        } catch (SQLException exception) {
            logger.atError()
                .setCause(exception)
                .addMarker(contextMarker)
                .log("Failed to create database table")
            ;
        }
    }
}
