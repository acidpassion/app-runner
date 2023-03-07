package com.danielflower.apprunner.runners;

import com.danielflower.apprunner.io.LineConsumer;
import com.danielflower.apprunner.problems.ProjectCannotStartException;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NodeRunner implements AppRunner {
    public static final Logger log = LoggerFactory.getLogger(NodeRunner.class);
    public static final String[] startCommands = new String[]{"npm install", "npm test", "node server.js"};

    private final File projectRoot;
    private final String nodeExec;
    private final String npmExec;
    private ExecuteWatchdog watchDog;

    public NodeRunner(File projectRoot, String nodeExec, String npmExec) {
        this.projectRoot = projectRoot;
        this.nodeExec = nodeExec;
        this.npmExec = npmExec;
    }

    @Override
    public File getInstanceDir() {
        return projectRoot;
    }

    public void start(LineConsumer buildLogHandler, LineConsumer consoleLogHandler, Map<String, String> envVarsForApp, Waiter startupWaiter) throws ProjectCannotStartException {
        runNPM(buildLogHandler, envVarsForApp, "install");
        runNPM(buildLogHandler, envVarsForApp, "test");

        CommandLine command = new CommandLine(nodeExec)
            .addArgument("server.js")
            .addArgument("--app-name=" + envVarsForApp.get("APP_NAME"));

        watchDog = ProcessStarter.startDaemon(buildLogHandler, consoleLogHandler, envVarsForApp, command, projectRoot, startupWaiter);
    }

    public void shutdown() {
        if (watchDog != null) {
            watchDog.destroyProcess();
            watchDog.stop();
        }
    }

    private void runNPM(LineConsumer buildLogHandler, Map<String, String> envVarsForApp, String argument) {
        CommandLine command = new CommandLine(npmExec).addArgument(argument);

        buildLogHandler.consumeLine("Running " + StringUtils.join(command.toStrings(), " "));
        ProcessStarter.run(buildLogHandler, envVarsForApp, command, projectRoot, TimeUnit.MINUTES.toMillis(30));
    }
}
