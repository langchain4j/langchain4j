package dev.langchain4j.code.local;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CodeExecutionEngine} that uses the local computer env to execute provided command line code.
 * The CommandLineExecutionEngine can be useful for Desktop Automation or Computer-Use Agents
 * (such as File Management, Application Control), like:
 * `open https://www.google.com`
 * `open -a "TextEdit"`
 * `osascript -e 'set volume output volume 50'`
 * `osascript -e 'say "hello world"'`
 *
 * Attention! It might be dangerous to execute the code in a production online serving environment.
 * It needs to be executed through security sandbox environment if used in online serving.
 */
public class CommandLineExecutionEngine implements CodeExecutionEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandLineExecutionEngine.class);

    @Override
    public String execute(String cmd) {
        DefaultExecutor executor = DefaultExecutor.builder().get();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
        executor.setStreamHandler(streamHandler);

        CommandLine cmdLine = CommandLine.parse(cmd);
        LOGGER.info("execute command line: {}", cmdLine);

        try {
            executor.execute(cmdLine);
            return outputStream.toString().trim();
        } catch (IOException e) {
            throw new RuntimeException(errorStream.toString());
        }
    }
}
