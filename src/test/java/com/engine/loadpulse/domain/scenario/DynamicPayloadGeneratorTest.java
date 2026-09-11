package com.engine.loadpulse.domain.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPayloadGeneratorTest {

    @Test
    @DisplayName("Should interpolate uuid(), timestamp(), counter(), and random_int()")
    void testDynamicInterpolation() {
        DynamicPayloadGenerator generator = new DynamicPayloadGenerator();

        String template = "{\"id\":\"{{ uuid() }}\",\"ts\":{{ timestamp() }},\"seq\":{{ counter() }},\"val\":{{ random_int(10, 20) }}}";

        String first = generator.interpolate(template);
        String second = generator.interpolate(template);

        assertThat(first).doesNotContain("{{");
        assertThat(first).doesNotContain("}}");

        // Verify UUID in first
        String uuidStr = first.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        assertThat(UUID.fromString(uuidStr)).isNotNull();

        // Verify counter increment
        assertThat(first).contains("\"seq\":1");
        assertThat(second).contains("\"seq\":2");
    }

    @Test
    @DisplayName("Should interpolate random_string() with specified length")
    void testRandomStringInterpolation() {
        DynamicPayloadGenerator generator = new DynamicPayloadGenerator();

        String template = "KEY_{{ random_string(16) }}";
        String result = generator.interpolate(template);

        assertThat(result).startsWith("KEY_");
        assertThat(result.substring(4)).hasSize(16);
    }
}
