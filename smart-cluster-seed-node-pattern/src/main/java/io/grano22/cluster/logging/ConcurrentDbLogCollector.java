package io.grano22.cluster.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
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
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConcurrentDbLogCollector extends AppenderBase<ILoggingEvent> {
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
    protected void append(ILoggingEvent iLoggingEvent) {
        toBeStored.add(iLoggingEvent);
    }

    private void storeLog(@NonNull ILoggingEvent iLoggingEvent) {
        try(var statement = connection.prepareStatement("INSERT INTO log_entries (message, mdc) VALUES (?, ?)")) {
            statement.setString(1, iLoggingEvent.getFormattedMessage());
            statement.setString(2, mapper.writeValueAsString(iLoggingEvent.getMDCPropertyMap()));

            statement.execute();
        } catch (SQLException exception) {
            logger.atError()
                .setCause(exception)
                .addMarker(contextMarker)
                .log("Failed to execute database query")
            ;
        }
    }

    private void createDatabase() {
        try(var statement = connection.createStatement()) {
            //DROP TABLE IF EXISTS log_entries;
            // createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            var result = statement.execute("""
            CREATE TABLE IF NOT EXISTS log_entries (
                id IDENTITY PRIMARY KEY,
                message VARCHAR(255),
                mdc TEXT,
                createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            )
            """);

            if (!result) {
                logger.atError()
                    .addMarker(contextMarker)
                    .log("Failed to create database table")
                ;

                return;
            }

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
