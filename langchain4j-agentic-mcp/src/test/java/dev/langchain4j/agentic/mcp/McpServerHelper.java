package dev.langchain4j.agentic.mcp;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpServerHelper {

    private static final Logger log = LoggerFactory.getLogger(McpServerHelper.class);

    static String getPathToScript(String script) {
        return ClassLoader.getSystemResource(script)
                .getFile()
                .substring(isWindows() ? 1 : 0)
                .replace("/", File.separator);
    }

    static String getJBangCommand() {
        String command = System.getProperty("jbang.command");
        if (isNullOrEmpty(command)) {
            command = isWindows() ? "jbang.cmd" : "jbang";
        }
        return command;
    }

    static void skipTestsIfJbangNotAvailable() {
        String command = getJBangCommand();
        try {
            new ProcessBuilder().command(command, "--version").start().waitFor();
        } catch (Exception e) {
            String message = "jbang is not available (could not execute command '" + command
                    + "', MCP integration tests will be skipped. "
                    + "The command may be overridden via the system property 'jbang.command'";
            log.warn(message, e);
            assumeTrue(false, message);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
