package com.engine.loadpulse.domain.scenario;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicPayloadGenerator {
    private static final Pattern PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)(\\((.*?)\\))?\\s*\\}\\}");
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final AtomicLong counter = new AtomicLong(1);

    public String interpolate(String template) {
        return interpolate(template, null, null);
    }

    public String interpolate(String template, Map<String, String> sessionVars, Map<String, String> feedRow) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String token = matcher.group(1);
            String functionName = token.toLowerCase();
            String argsStr = matcher.group(3);

            String replacement = resolve(token, functionName, argsStr, sessionVars, feedRow);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolve(
            String originalToken,
            String functionName,
            String argsStr,
            Map<String, String> sessionVars,
            Map<String, String> feedRow
    ) {
        if (originalToken.startsWith("vars.") && sessionVars != null) {
            String key = originalToken.substring("vars.".length());
            return sessionVars.getOrDefault(key, "");
        }

        if (originalToken.startsWith("feed.") && feedRow != null) {
            String key = originalToken.substring("feed.".length());
            return feedRow.getOrDefault(key, "");
        }

        return switch (functionName) {
            case "uuid" -> UUID.randomUUID().toString();
            case "timestamp" -> String.valueOf(System.currentTimeMillis());
            case "iso_timestamp" -> Instant.now().toString();
            case "counter" -> String.valueOf(counter.getAndIncrement());
            case "random_int" -> {
                int min = 1;
                int max = 1000;
                if (argsStr != null && !argsStr.isBlank()) {
                    String[] parts = argsStr.split(",");
                    if (parts.length >= 2) {
                        try {
                            min = Integer.parseInt(parts[0].trim());
                            max = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (parts.length == 1) {
                        try {
                            max = Integer.parseInt(parts[0].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (max < min) {
                    int tmp = min;
                    min = max;
                    max = tmp;
                }
                yield String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
            }
            case "random_string" -> {
                int length = 8;
                if (argsStr != null && !argsStr.isBlank()) {
                    try {
                        length = Math.max(1, Integer.parseInt(argsStr.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
                Random random = ThreadLocalRandom.current();
                char[] chars = new char[length];
                for (int i = 0; i < length; i++) {
                    chars[i] = ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length()));
                }
                yield new String(chars);
            }
            default -> "{{" + originalToken + (argsStr != null ? "(" + argsStr + ")" : "") + "}}";
        };
    }

    public void resetCounter() {
        counter.set(1);
    }
}
