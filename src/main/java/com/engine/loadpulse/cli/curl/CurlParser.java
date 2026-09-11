package com.engine.loadpulse.cli.curl;

import com.engine.loadpulse.domain.model.HttpMethod;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CurlParser {

    public record ParsedCurl(
            URI targetUri,
            HttpMethod method,
            Map<String, String> headers,
            String body
    ) {}

    public static ParsedCurl parse(String curlCommand) {
        if (curlCommand == null || curlCommand.isBlank()) {
            throw new IllegalArgumentException("cURL command cannot be empty");
        }

        List<String> tokens = tokenize(curlCommand.trim());
        if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase("curl")) {
            throw new IllegalArgumentException("Command must start with 'curl'");
        }

        String targetUrl = null;
        HttpMethod method = null;
        Map<String, String> headers = new HashMap<>();
        String body = null;

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (token.equals("-X") || token.equalsIgnoreCase("--request")) {
                if (++i < tokens.size()) {
                    method = HttpMethod.fromString(tokens.get(i));
                }
            } else if (token.equals("-H") || token.equalsIgnoreCase("--header")) {
                if (++i < tokens.size()) {
                    String headerVal = tokens.get(i);
                    int colonIdx = headerVal.indexOf(':');
                    if (colonIdx > 0) {
                        headers.put(headerVal.substring(0, colonIdx).trim(), headerVal.substring(colonIdx + 1).trim());
                    }
                }
            } else if (token.equals("-d") || token.equalsIgnoreCase("--data")
                    || token.equalsIgnoreCase("--data-raw") || token.equalsIgnoreCase("--data-binary")) {
                if (++i < tokens.size()) {
                    body = tokens.get(i);
                    if (method == null) {
                        method = HttpMethod.POST;
                    }
                }
            } else if (token.equals("-u") || token.equalsIgnoreCase("--user")) {
                if (++i < tokens.size()) {
                    String userPass = tokens.get(i);
                    String encoded = Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));
                    headers.put("Authorization", "Basic " + encoded);
                }
            } else if (!token.startsWith("-")) {
                if (targetUrl == null) {
                    targetUrl = token;
                }
            }
        }

        if (targetUrl == null) {
            throw new IllegalArgumentException("Could not find a valid URL in the cURL command");
        }

        if (method == null) {
            method = HttpMethod.GET;
        }

        return new ParsedCurl(URI.create(targetUrl), method, headers, body);
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escapeNext = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escapeNext) {
                sb.append(c);
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (!sb.isEmpty()) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }

        if (!sb.isEmpty()) {
            tokens.add(sb.toString());
        }

        return tokens;
    }
}
