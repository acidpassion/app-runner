package com.danielflower.apprunner;

import com.danielflower.apprunner.problems.AppRunnerException;
import com.danielflower.apprunner.problems.InvalidConfigException;
import com.danielflower.apprunner.runners.CommandLineProvider;
import com.danielflower.apprunner.runners.ExplicitJavaHome;
import com.danielflower.apprunner.runners.HomeProvider;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.danielflower.apprunner.FileSandbox.fullPath;

public class Config {
    public static final String SERVER_HTTP_PORT = "appserver.port";
    public static final String SERVER_HTTPS_PORT = "appserver.https.port";
    public static final String DATA_DIR = "appserver.data.dir";
    public static final String DEFAULT_APP_NAME = "appserver.default.app.name";
    public static final String INITIAL_APP_URL = "appserver.initial.app.url";
    public static final String BACKUP_URL = "appserver.backup.url";
    public static final String BACKUP_MINUTES = "appserver.backup.schedule.minutes";
    public static final String GIT_URL_VALIDATION_REGEX = "apprunner.git.url.validation.regex";
    public static final String GIT_URL_VALIDATION_MESSAGE = "apprunner.git.url.validation.message";

    public static final String JAVA_HOME = "java.home";
    public static final String M2_HOME = "m2.home";
    public static final String LEIN_JAR = "lein.jar";
    public static final String LEIN_JAVA_CMD = "lein.java.cmd";

    public static final String SBT_JAR = "sbt-launcher.jar";
    public static final String SBT_JAVA_CMD = "sbt.java.cmd";

    public static final String GOROOT = "goroot";

    public static final Logger log = LoggerFactory.getLogger(Config.class);

    public static Config load(String[] commandLineArgs) throws IOException {
        Map<String, String> systemEnv = System.getenv();
        Map<String, String> env = new HashMap<>(systemEnv);
        for (Map.Entry<String, String> s : systemEnv.entrySet()) {
            String envVarKey = s.getKey().toLowerCase().replace('_', '.');
            env.put(envVarKey, s.getValue());
        }
        for (String key : System.getProperties().stringPropertyNames()) {
            String value = System.getProperty(key);
            env.put(key, value);
        }
        for (String commandLineArg : commandLineArgs) {
            File file = new File(commandLineArg);
            if (file.isFile()) {
                log.info("Using config file: " + file.getAbsolutePath());
                Properties props = new Properties();
                try (FileInputStream inStream = new FileInputStream(file)) {
                    props.load(inStream);
                }
                for (String key : props.stringPropertyNames()) {
                    env.put(key, props.getProperty(key));
                }
            }
        }
        return new Config(env);
    }

    public AppRunnerHooks hooks = new AppRunnerHooks() {};
    private final Map<String, String> raw;

    public Config(Map<String, String> raw) {
        this.raw = raw;
    }

    public CommandLineProvider leinJavaCommandProvider() {
        return raw.containsKey(LEIN_JAVA_CMD)
            ? (Map<String, String> env) -> new CommandLine(getFile(LEIN_JAVA_CMD))
            : javaHomeProvider();
    }

    public CommandLineProvider leinCommandProvider() {
        return raw.containsKey(LEIN_JAR)
            ? (Map<String, String> env) -> leinJavaCommandProvider()
                        .commandLine(env)
                        .addArgument("-cp")
                        .addArgument(fullPath(getFile(LEIN_JAR)))
                        .addArgument("-Djava.io.tmpdir=" + env.get("TEMP"))
                        .addArgument("clojure.main")
                        .addArgument("-m")
                        .addArgument("leiningen.core.main")
            : CommandLineProvider.lein_on_path;
    }

    public CommandLineProvider sbtJavaCommandProvider() {
        return raw.containsKey(SBT_JAVA_CMD)
            ? (Map<String, String> env) ->

            new CommandLine(getFile(SBT_JAVA_CMD))

            : javaHomeProvider();
    }

    public CommandLineProvider sbtCommandProvider() {
        return raw.containsKey(SBT_JAR)
            ? (Map<String, String> env) ->

            sbtJavaCommandProvider()
                .commandLine(env)
                .addArgument("-cp")
                .addArgument(fullPath(getFile(SBT_JAR)))
                .addArgument("-Djava.io.tmpdir=" + env.get("TEMP"))

            : CommandLineProvider.sbt_on_path;
    }

    public CommandLineProvider goCommandProvider() {
        return raw.containsKey(GOROOT)
            ? (Map<String, String> env) ->
            new CommandLine(fullPath(getDir(GOROOT)) + File.separator + "bin" + File.separator + goExecutableName())
            : CommandLineProvider.go_on_path;
    }

    public String pythonVirtualEnvExecutable(int majorVersion) {
        return get("python." + majorVersion + ".virtualenv.exec", windowsinize("virtualenv"));
    }

    public String pythonExecutable(int majorVersion) {
        return get("python." + majorVersion + ".exec", SystemUtils.IS_OS_WINDOWS ? "python.exe" : "python" + majorVersion);
    }

    public String cargoExecutable() { return get("cargo.exec", windowsinize("cargo")); }

    public String nodeExecutable() {
        return get("node.exec", windowsinize("node"));
    }

    public String npmExecutable() {
        return get("npm.exec", SystemUtils.IS_OS_WINDOWS ? "npm.cmd" : "npm");
    }

    public HomeProvider javaHomeProvider() {
        return raw.containsKey(JAVA_HOME)
            ? new ExplicitJavaHome(getDir(JAVA_HOME))
            : HomeProvider.default_java_home;
    }

    public static String goExecutableName() {
        return windowsinize("go");
    }

    public static String dotnetExecutableName() {
        return windowsinize("dotnet");
    }

    public static String javaExecutableName() {
        return windowsinize("java");
    }

    public static String gradleExecutableName() {
        return windowsinize("gradle");
    }

    private static String windowsinize(String command) {
        return SystemUtils.IS_OS_WINDOWS ? command + ".exe" : command;
    }

    public String get(String name, String defaultVal) {
        return raw.getOrDefault(name, defaultVal);
    }

    public String get(String name) {
        String s = get(name, null);
        if (s == null) {
            throw new InvalidConfigException("Missing config item: " + name);
        }
        return s;
    }

    public int getInt(String name, int defaultValue) {
        String s = get(name, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(s, 10);
        } catch (NumberFormatException e) {
            throw new InvalidConfigException("Could not convert " + name + "=" + s + " to an integer");
        }
    }

    public long getLong(String name, long defaultValue) {
        String s = get(name, String.valueOf(defaultValue));
        try {
            return Long.parseLong(s, 10);
        } catch (NumberFormatException e) {
            throw new InvalidConfigException("Could not convert " + name + "=" + s + " to an integer");
        }
    }

    public File getDir(String name) {
        File f = new File(get(name));
        if (!f.isDirectory()) {
            throw new AppRunnerException("Could not find " + name + " directory: " + fullPath(f));
        }
        return f;
    }

    public File getOrCreateDir(String name) {
        File f = new File(get(name));
        try {
            FileUtils.forceMkdir(f);
        } catch (IOException e) {
            throw new AppRunnerException("Could not create " + fullPath(f));
        }
        return f;
    }

    public File getFile(String name) {
        File f = new File(get(name));
        if (!f.isFile()) {
            throw new AppRunnerException("Could not find " + name + " file: " + fullPath(f));
        }
        return f;
    }

    public Map<String, String> env() {
        return new HashMap<>(raw);
    }
}

