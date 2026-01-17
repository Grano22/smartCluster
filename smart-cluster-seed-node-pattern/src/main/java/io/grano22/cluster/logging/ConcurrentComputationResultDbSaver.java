package io.grano22.cluster.logging;

import io.grano22.cluster.UIJob;
import io.grano22.cluster.runtime.ExecutionRuntime;
import lombok.NonNull;
import org.apache.tools.ant.taskdefs.Exec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ConcurrentComputationResultDbSaver {
    private static final Marker contextMarker = MarkerFactory.getMarker("Computation DB Saver");
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentComputationResultDbSaver.class);

    private Connection connection = null;
    private final ConnectionSource connectionSource;
    private final Deque<ExecutionRuntime.UnionResult> savingQue = new ConcurrentLinkedDeque<>();

    public ConcurrentComputationResultDbSaver(@NonNull ConnectionSource connectionSource) {
        this.connectionSource = connectionSource;
        connect();
    }

    public void saveResults(@NonNull ExecutionRuntime.ExecutionResult result) {
        if (result instanceof ExecutionRuntime.UnionResult unionResult) {
            for (var subResult : unionResult.results()) {
                if (subResult instanceof ExecutionRuntime.Result simpleResult) {
                    saveResult(simpleResult);
                }
            }
        }

        if (result instanceof ExecutionRuntime.Result simpleResult) {
            saveResult(simpleResult);
        }
    }

    public void saveResult(@NonNull ExecutionRuntime.Result result) {
        try (var statement = connection.prepareStatement("INSERT INTO execution_entries (statusCode, output) VALUES (?, ?)")) {
            statement.setString(1, String.valueOf(result.statusCode()));
            statement.setString(2, result.output());
            statement.execute();
        } catch (SQLException exception) {
            logger.atError()
                .addMarker(contextMarker)
                .log("Failed to save result to the DB")
            ;
        }
    }

    private void connect() {
        try {
            this.connection = Objects.requireNonNull(
                DriverManager.getConnection(connectionSource.getUrl(), connectionSource.getUser(), connectionSource.getPassword()),
                "Connection is required"
            );
        } catch (SQLException exception) {
            logger.atError()
                .setCause(exception)
                .addMarker(contextMarker)
                .log("Failed to connect to the database");
            ;
        }
    }

    private void prepare() {
        try(var statement = connection.createStatement()) {
            statement.execute("""
            CREATE TABLE IF NOT EXISTS execution_entries (
                id IDENTITY PRIMARY KEY,
                statusCode TINYINT,
                output TEXT,
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
