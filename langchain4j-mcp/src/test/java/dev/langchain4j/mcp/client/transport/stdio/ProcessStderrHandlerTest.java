package dev.langchain4j.mcp.client.transport.stdio;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessStderrHandlerTest {

    @Test
    void shouldLabelProcessStderrOutputAsStderr() {
        try (MockedStatic<LoggerFactory> loggerFactory = mockStatic(LoggerFactory.class)) {
            Logger logger = mock(Logger.class);
            loggerFactory
                    .when(() -> LoggerFactory.getLogger(ProcessStderrHandler.class))
                    .thenReturn(logger);

            Process process = mock(Process.class);
            when(process.getErrorStream())
                    .thenReturn(new ByteArrayInputStream(("server log" + System.lineSeparator()).getBytes()));
            when(process.pid()).thenReturn(123L);

            new ProcessStderrHandler(process).run();

            verify(logger).debug("[STDERR] {}", "server log");
            verify(logger, never()).debug("[ERROR] {}", "server log");
        }
    }
}
