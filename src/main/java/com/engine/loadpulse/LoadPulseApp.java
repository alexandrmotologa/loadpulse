package com.engine.loadpulse;

import com.engine.loadpulse.cli.DiffCommand;
import com.engine.loadpulse.cli.FromCurlCommand;
import com.engine.loadpulse.cli.MockServerCommand;
import com.engine.loadpulse.cli.RunCommand;
import com.engine.loadpulse.cli.ScenarioCommand;
import com.engine.loadpulse.cli.WebSocketCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "loadpulse",
        description = "Reactive HTTP/1.1, HTTP/2 & gRPC load testing engine powered by Java 21 Virtual Threads and HdrHistogram",
        version = "loadpulse 1.1.0",
        subcommands = {
                RunCommand.class,
                ScenarioCommand.class,
                FromCurlCommand.class,
                DiffCommand.class,
                WebSocketCommand.class,
                MockServerCommand.class,
                LoadPulseApp.VersionCommand.class
        }
)
public class LoadPulseApp implements Runnable {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    boolean helpRequested;

    @Option(names = {"-v", "-V", "--version"}, versionHelp = true, description = "Print version information and exit.")
    boolean versionRequested;

    @Command(name = "version", description = "Print version information and exit")
    public static class VersionCommand implements Runnable {
        @Override
        public void run() {
            System.out.println("loadpulse 1.1.0");
        }
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new LoadPulseApp());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
