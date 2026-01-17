package io.grano22.cluster.logging;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

public record LoggerContext(Map<String, String> context) {
    public static LoggerContext create() {
        return new LoggerContext(Map.of());
    }

    public LoggerContext withWebHost(@NonNull String webHost) {
        var clonedMutableMap = new HashMap<>(context);
        clonedMutableMap.put("webHost", webHost);
        var clonedContext = Map.copyOf(clonedMutableMap);

        return new LoggerContext(clonedContext);
    }
}
