package com.engine.loadpulse.cli;

import com.engine.loadpulse.LoadPulseApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class LoadPulseAppVersionTest {

    @Test
    @DisplayName("Should display version with --version flag")
    void testVersionFlagLong() {
        CommandLine cmd = new CommandLine(new LoadPulseApp());
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("--version");

        assertThat(exitCode).isEqualTo(0);
        assertThat(sw.toString().trim()).isEqualTo("loadpulse 1.1.0");
    }

    @Test
    @DisplayName("Should display version with -v flag")
    void testVersionFlagShort() {
        CommandLine cmd = new CommandLine(new LoadPulseApp());
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("-v");

        assertThat(exitCode).isEqualTo(0);
        assertThat(sw.toString().trim()).isEqualTo("loadpulse 1.1.0");
    }

    @Test
    @DisplayName("Should display version with version subcommand")
    void testVersionSubcommand() {
        CommandLine cmd = new CommandLine(new LoadPulseApp());
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("version");

        assertThat(exitCode).isEqualTo(0);
    }
}
