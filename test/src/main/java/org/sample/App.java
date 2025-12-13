package org.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.simple.SimpleLogger;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class App {
    static {
        Properties properties = new Properties();
        properties.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "warn");
        properties.setProperty("org.slf4j.simpleLogger.log.org.sample", "info");
        properties.setProperty(SimpleLogger.LOG_FILE_KEY, "System.out");
        properties.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
        properties.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "true");
        properties.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "true");
        System.setProperties(properties);
    }

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        logger.info("Cluster is starting...");
        NodeConfig config = NodeConfigLoader.load(
            Path.of(
                Optional.ofNullable(System.getenv("CONFIG_FILE"))
                    .orElse("config.json")
            )
        );
        NodesMeshManager nodesMeshManager = NodesMeshManager.initMeshFromConfig(config);

        Thread uiJob = new Thread(new UIJob(config.webPort(), nodesMeshManager));
        uiJob.start();

        logger.info("Cluster started, ready to act");

        uiJob.join();
    }
}
