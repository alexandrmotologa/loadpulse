package com.engine.loadpulse.cli.curl;

import com.engine.loadpulse.domain.model.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurlParserTest {

    @Test
    @DisplayName("Should parse standard GET cURL command")
    void testParseBasicGetCurl() {
        String cmd = "curl https://api.example.com/v1/users";
        CurlParser.ParsedCurl parsed = CurlParser.parse(cmd);

        assertThat(parsed.targetUri().toString()).isEqualTo("https://api.example.com/v1/users");
        assertThat(parsed.method()).isEqualTo(HttpMethod.GET);
        assertThat(parsed.headers()).isEmpty();
        assertThat(parsed.body()).isNull();
    }

    @Test
    @DisplayName("Should parse complex POST cURL with headers and JSON body")
    void testParsePostCurlWithHeadersAndBody() {
        String cmd = """
                curl 'https://api.example.com/v1/orders' \
                  -X POST \
                  -H 'Content-Type: application/json' \
                  -H 'Authorization: Bearer token-xyz' \
                  --data-raw '{"productId": 101, "qty": 2}'
                """;

        CurlParser.ParsedCurl parsed = CurlParser.parse(cmd);

        assertThat(parsed.targetUri().toString()).isEqualTo("https://api.example.com/v1/orders");
        assertThat(parsed.method()).isEqualTo(HttpMethod.POST);
        assertThat(parsed.headers()).containsEntry("Content-Type", "application/json");
        assertThat(parsed.headers()).containsEntry("Authorization", "Bearer token-xyz");
        assertThat(parsed.body()).isEqualTo("{\"productId\": 101, \"qty\": 2}");
    }

    @Test
    @DisplayName("Should parse basic authentication (-u) flag")
    void testParseBasicAuth() {
        String cmd = "curl https://api.example.com/secure -u admin:secret123";
        CurlParser.ParsedCurl parsed = CurlParser.parse(cmd);

        assertThat(parsed.headers()).containsKey("Authorization");
        assertThat(parsed.headers().get("Authorization")).startsWith("Basic ");
    }

    @Test
    @DisplayName("Should fail when command does not start with curl")
    void testInvalidCurlCommand() {
        assertThatThrownBy(() -> CurlParser.parse("wget http://localhost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command must start with 'curl'");
    }
}
