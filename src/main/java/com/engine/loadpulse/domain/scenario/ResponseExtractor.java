package com.engine.loadpulse.domain.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ResponseExtractor {

    public static Map<String, String> extract(
            Map<String, String> extractionRules,
            HttpResponse<byte[]> response,
            ObjectMapper mapper
    ) {
        if (extractionRules == null || extractionRules.isEmpty() || response == null) {
            return Collections.emptyMap();
        }

        Map<String, String> extracted = new HashMap<>();
        JsonNode rootNode = null;

        for (Map.Entry<String, String> entry : extractionRules.entrySet()) {
            String varName = entry.getKey();
            String path = entry.getValue().trim();

            if (path.startsWith("headers.")) {
                String headerName = path.substring("headers.".length()).trim();
                response.headers().firstValue(headerName).ifPresent(val -> extracted.put(varName, val));
            } else if (path.startsWith("status")) {
                extracted.put(varName, String.valueOf(response.statusCode()));
            } else if (path.startsWith("$")) {
                if (rootNode == null && response.body() != null && response.body().length > 0) {
                    try {
                        String bodyStr = new String(response.body(), StandardCharsets.UTF_8);
                        rootNode = mapper.readTree(bodyStr);
                    } catch (Exception ignored) {
                    }
                }

                if (rootNode != null) {
                    String val = extractJsonPath(rootNode, path);
                    if (val != null) {
                        extracted.put(varName, val);
                    }
                }
            }
        }

        return extracted;
    }

    public static String extractJsonPath(JsonNode root, String path) {
        if (root == null || path == null) return null;

        // Path format: $.data.user.id or $.items[0].id
        String normalized = path.startsWith("$.") ? path.substring(2) : (path.startsWith("$") ? path.substring(1) : path);
        if (normalized.isEmpty()) {
            return root.asText();
        }

        String[] tokens = normalized.split("\\.");
        JsonNode current = root;

        for (String token : tokens) {
            if (current == null) return null;

            if (token.contains("[") && token.endsWith("]")) {
                int bracketIdx = token.indexOf('[');
                String field = token.substring(0, bracketIdx);
                int index = Integer.parseInt(token.substring(bracketIdx + 1, token.length() - 1));

                if (!field.isEmpty()) {
                    current = current.path(field);
                }
                if (current.isArray() && index < current.size()) {
                    current = current.get(index);
                } else {
                    return null;
                }
            } else {
                current = current.path(token);
            }
        }

        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }

        return current.isValueNode() ? current.asText() : current.toString();
    }
}
