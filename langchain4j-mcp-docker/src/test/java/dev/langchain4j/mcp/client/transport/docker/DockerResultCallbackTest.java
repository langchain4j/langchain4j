package dev.langchain4j.mcp.client.transport.docker;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DockerResultCallbackTest {

    @Test
    void shouldLabelDockerStderrFramesAsStderr() {
        try (MockedStatic<LoggerFactory> loggerFactory = mockStatic(LoggerFactory.class)) {
            Logger logger = mock(Logger.class);
            loggerFactory
                    .when(() -> LoggerFactory.getLogger(DockerResultCallback.class))
                    .thenReturn(logger);
            McpOperationHandler messageHandler = mock(McpOperationHandler.class);
            DockerResultCallback callback = new DockerResultCallback(false, messageHandler);

            callback.onNext(new Frame(StreamType.STDERR, "server log".getBytes(UTF_8)));

            verify(logger).debug("[STDERR] {}", "server log");
            verify(logger, never()).debug("[ERROR] {}", "server log");
            verifyNoInteractions(messageHandler);
        }
    }
}
