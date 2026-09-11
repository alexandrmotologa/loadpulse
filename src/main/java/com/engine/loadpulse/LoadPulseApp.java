package com.engine.loadpulse;

import com.engine.loadpulse.cli.DiffCommand;
import com.engine.loadpulse.cli.FromCurlCommand;
import com.engine.loadpulse.cli.MockServerCommand;
import com.engine.loadpulse.cli.RunCommand;
import com.engine.loadpulse.cli.ScenarioCommand;
import com.engine.loadpulse.cli.WebSocketCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "loadpulse",
        description = "Reactive HTTP/1.1, HTTP/2 & gRPC load testing engine powered by Java 21 Virtual Threads and HdrHistogram",
        mixinStandardHelpOptions = true,
        version = "loadpulse 1.1.0",
        subcommands = {
                RunCommand.class,
                ScenarioCommand.class,
                FromCurlCommand.class,
                DiffCommand.class,
                WebSocketCommand.class,
                MockServerCommand.class
        }
)
public class LoadPulseApp implements Runnable {

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
