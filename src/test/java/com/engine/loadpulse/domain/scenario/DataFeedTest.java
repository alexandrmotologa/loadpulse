package com.engine.loadpulse.domain.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataFeedTest {

    @Test
    @DisplayName("Should parse CSV file and supply rows in round-robin order")
    void testCsvDataFeedRoundRobin(@TempDir Path tempDir) throws Exception {
        Path csvFile = tempDir.resolve("users.csv");
        Files.writeString(csvFile, """
                username,role,accountId
                alice,admin,ACC-101
                bob,developer,ACC-102
                charlie,viewer,ACC-103
                """);

        DataFeed.DataFeedConfig config = new DataFeed.DataFeedConfig(csvFile.toString(), "round-robin");
        DataFeed feed = DataFeed.fromConfig(config);

        assertThat(feed).isNotNull();
        assertThat(feed.getRowCount()).isEqualTo(3);

        Map<String, String> row1 = feed.nextRow();
        assertThat(row1.get("username")).isEqualTo("alice");
        assertThat(row1.get("role")).isEqualTo("admin");
        assertThat(row1.get("accountId")).isEqualTo("ACC-101");

        Map<String, String> row2 = feed.nextRow();
        assertThat(row2.get("username")).isEqualTo("bob");

        Map<String, String> row3 = feed.nextRow();
        assertThat(row3.get("username")).isEqualTo("charlie");

        // Wraps around round-robin
        Map<String, String> row4 = feed.nextRow();
        assertThat(row4.get("username")).isEqualTo("alice");
    }
}
