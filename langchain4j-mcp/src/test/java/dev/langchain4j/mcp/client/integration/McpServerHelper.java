package dev.langchain4j.mcp.client.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpServerHelper {

    private static final Logger log = LoggerFactory.getLogger(McpToolsHttpTransportIT.class);

    static Process startServerHttp(String scriptName) throws InterruptedException, TimeoutException, IOException {
        return startServerHttp(scriptName, 8080);
    }

    static Process startServerHttp(String scriptName, int port) throws InterruptedException, TimeoutException, IOException {
        skipTestsIfJbangNotAvailable();
        String path = getPathToScript(scriptName);
        String[] command = new String[] {getJBangCommand(), "--quiet", "--fresh", "run", "-Dquarkus.http.port=" + port, path};
        log.info("Starting the MCP server using command: " + Arrays.toString(command));
        Process process = new ProcessBuilder().command(command).inheritIO().start();
        waitForPort(port, 120);
        log.info("MCP server has started");
        return process;
    }

    static String getPathToScript(String script) {
        return ClassLoader.getSystemResource(script)
                .getFile()
                .substring(isWindows() ? 1 : 0)
                .replace("/", File.separator);
    }

    static String getJBangCommand() {
        String command = System.getProperty("jbang.command");
        if (command == null || command.isEmpty()) {
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

    private static void waitForPort(int port, int timeoutSeconds) throws InterruptedException, TimeoutException {
        Request request = new Request.Builder().url("http://localhost:" + port).build();
        long start = System.currentTimeMillis();
        OkHttpClient client = new OkHttpClient();
        while (System.currentTimeMillis() - start < timeoutSeconds * 1000) {
            try {
                client.newCall(request).execute();
                return;
            } catch (IOException e) {
                TimeUnit.SECONDS.sleep(1);
            }
        }
        throw new TimeoutException("Port " + port + " did not open within " + timeoutSeconds + " seconds");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
