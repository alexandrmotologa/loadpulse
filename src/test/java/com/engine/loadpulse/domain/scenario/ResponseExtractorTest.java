package com.engine.loadpulse.domain.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Should extract fields from JSON path queries")
    void testExtractJsonPath() throws Exception {
        String json = """
                {
                  "status": "success",
                  "data": {
                    "token": "jwt-token-12345",
                    "user": {
                      "id": 987,
                      "email": "test@example.com"
                    },
                    "roles": ["admin", "developer"]
                  }
                }
                """;

        JsonNode root = mapper.readTree(json);

        assertThat(ResponseExtractor.extractJsonPath(root, "$.status")).isEqualTo("success");
        assertThat(ResponseExtractor.extractJsonPath(root, "$.data.token")).isEqualTo("jwt-token-12345");
        assertThat(ResponseExtractor.extractJsonPath(root, "$.data.user.id")).isEqualTo("987");
        assertThat(ResponseExtractor.extractJsonPath(root, "$.data.roles[0]")).isEqualTo("admin");
        assertThat(ResponseExtractor.extractJsonPath(root, "$.data.roles[1]")).isEqualTo("developer");
        assertThat(ResponseExtractor.extractJsonPath(root, "$.nonexistent")).isNull();
    }
}
