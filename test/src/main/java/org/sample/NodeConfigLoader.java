package org.sample;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonReadContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class NodeConfigLoader {
    private final static JsonMapper MAPPER = JsonMapper.shared();

    public static NodeConfig load(Path configPath) {
        if (configPath == null) {
            throw new RuntimeException("Path must be set");
        }

        String hostname = "localhost";
        int webPort = 0, tcpPort = 0, heartbeatPort = 0;
        Set<String> nodesToDiscover = new HashSet<>();
        Set<NodeConfig.ClusterSettingsForNode> clusterSettingsForNodes = new HashSet<>();

        try(InputStream is = Files.newInputStream(configPath); JsonParser parser = MAPPER.reader().createParser(is)) {
            int rootDepth = parser.streamReadContext().getNestingDepth();
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new RuntimeException("Expected {");

            while (parser.nextToken() != JsonToken.END_OBJECT || rootDepth != parser.streamReadContext().getNestingDepth()) {
                String name = parser.currentName();
                if (name == null) continue;

                parser.nextToken();

                switch (name) {
                    case "hostname" -> {
                        hostname = parser.getString();
                    }
                    case "webPort" -> {
                        webPort = parser.getIntValue();
                    }
                    case "communicationPort" -> {
                        tcpPort = parser.getIntValue();
                    }
                    case "heartbeatPort" -> {
                        heartbeatPort = parser.getIntValue();
                    }
                    case "discoveryNodes" -> {
                        if (parser.currentToken() != JsonToken.START_ARRAY) {
                            throw new IllegalStateException("'discoveryNodes' must be an array");
                        }

                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            nodesToDiscover.add(parser.getString());
                        }
                    }
                    case "clusters" -> {
                        if (parser.currentToken() != JsonToken.START_ARRAY) {
                            throw new IllegalStateException("'clusters' must be an array");
                        }

                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            var nextNodeClusterSettings = parser.readValueAs(JsonNode.class);
                            String clusterName = nextNodeClusterSettings.requiredAt("/name").asString();
                            clusterSettingsForNodes.add(new NodeConfig.ClusterSettingsForNode(clusterName));
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new NodeConfig(
            hostname,
            webPort,
            tcpPort,
            heartbeatPort,
            nodesToDiscover,
            clusterSettingsForNodes
        );
    }
}
