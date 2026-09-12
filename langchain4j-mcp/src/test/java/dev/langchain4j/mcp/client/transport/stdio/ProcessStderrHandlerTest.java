package dev.langchain4j.mcp.client.transport.stdio;

import static java.nio.charset.StandardCharsets.UTF_8;
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

    @Test
    void shouldDecodeProcessStderrOutputAsUtf8() {
        try (MockedStatic<LoggerFactory> loggerFactory = mockStatic(LoggerFactory.class)) {
            Logger logger = mock(Logger.class);
            loggerFactory
                    .when(() -> LoggerFactory.getLogger(ProcessStderrHandler.class))
                    .thenReturn(logger);

            Process process = mock(Process.class);
            when(process.getErrorStream())
                    .thenReturn(new ByteArrayInputStream(
                            ("caf\u00e9 \ud55c\uae00 \ud83d\ude80" + System.lineSeparator()).getBytes(UTF_8)));
            when(process.pid()).thenReturn(123L);

            new ProcessStderrHandler(process).run();

            verify(logger).debug("[STDERR] {}", "caf\u00e9 \ud55c\uae00 \ud83d\ude80");
        }
    }
}
