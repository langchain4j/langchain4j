package dev.langchain4j.mcp.client.transport.stdio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessStderrHandler implements Runnable {

    private final Process process;
    private static final Logger log = LoggerFactory.getLogger(ProcessStderrHandler.class);

    public ProcessStderrHandler(final Process process) {
        this.process = process;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[ERROR] {}", line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.debug("ProcessErrorPrinter has finished reading error output from process with PID = " + process.pid());
    }
}
