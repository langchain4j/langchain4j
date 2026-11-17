package dev.langchain4j.mcp.client.transport.docker;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.tinylog.Level;

class DockerResultCallbackTest {

    @Test
    @ResourceLock("docker-callback-logs")
    void shouldLabelDockerStderrFramesAsStderr() {
        McpOperationHandler messageHandler = mock(McpOperationHandler.class);
        // Exercise logging after the callback's static logger has already been initialized.
        DockerResultCallback callback = new DockerResultCallback(false, messageHandler);
        DockerCallbackTestWriter.entries.clear();
        callback.onNext(new Frame(StreamType.STDERR, "server log".getBytes(UTF_8)));

        assertThat(DockerCallbackTestWriter.entries).singleElement().satisfies(entry -> {
            assertThat(entry.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(entry.getMessage()).isEqualTo("[STDERR] server log");
        });
        verifyNoInteractions(messageHandler);
    }

    @Test
    void shouldDecodeDockerFramesAsUtf8() {
        McpOperationHandler messageHandler = mock(McpOperationHandler.class);
        DockerResultCallback callback = new DockerResultCallback(false, messageHandler);
        String message = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"caf\u00e9 \ud55c\uae00 \ud83d\ude80\"}";

        callback.onNext(new Frame(StreamType.STDOUT, (message + "\n").getBytes(UTF_8)));

        verify(messageHandler).onMessage(message + "\n");
    }
}
