package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class DefaultMcpClientTest {
    @Test
    public void should_construct_valid_client_with_minimally_mocked_transport() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();

        // when
        final DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();

        // then: client is properly constructed
        assertThat(client).isNotNull();
        // and: transport failure callback is set
        verify(transport).onFailure(any());
        // and: transport is started
        verify(transport).start(any());
        // and: transport is left open
        verify(transport, never()).close();
    }

    @Test
    public void should_close_transport_when_client_is_closed() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();

        // when
        client.close();

        // then: the transport is closed
        verify(transport).close();
    }

    @Test
    public void should_reinitialize_when_transport_onFailure() throws Exception {
        // given: a client that will have its onFailure callback run
        final McpTransport transport = getMinimalMcpTransportMock();
        final ArgumentCaptor<Runnable> onFailureCaptor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(transport).onFailure(onFailureCaptor.capture());

        new DefaultMcpClient.Builder().transport(transport).build();

        // sanity check: transport was started
        verify(transport).start(any(McpOperationHandler.class));

        // when: onFailure callback is triggered
        onFailureCaptor.getValue().run();

        // then: a second transport start occurred
        verify(transport, times(2)).start(any(McpOperationHandler.class));
    }

    @Test
    public void should_throw_from_build_when_there_is_no_transport() {
        // given
        final DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder();

        // when
        assertThatThrownBy(() -> clientBuilder.build())
                // then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transport")
                .hasMessageContaining("null");
    }

    @Test
    public void should_throw_from_build_when_transport_initialize_throws() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final var exception = new RuntimeException("apples");
        doThrow(exception).when(transport).initialize(any());
        final DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder().transport(transport);

        // when
        assertThatThrownBy(() -> clientBuilder.build())
                // then: DefaultMcpClient's initializer wraps all exceptions
                // in its own RuntimeException at the moment
                .isInstanceOf(Throwable.class)
                .hasMessageContaining("apples");

        // and: transport should be started
        verify(transport).start(any(McpOperationHandler.class));
        // and: transport should *not* be closed
        verify(transport, never()).close();
    }

    @Test
    public void should_not_react_to_transport_callbacks_if_there_is_no_object() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final var exception = new RuntimeException("apples");
        final ArgumentCaptor<Runnable> onFailureCaptor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(transport).onFailure(onFailureCaptor.capture());
        doThrow(exception).when(transport).initialize(any());
        final DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder().transport(transport);

        // and: exception was thrown during initialization (so there is no object)
        assertThatThrownBy(() -> clientBuilder.build()).isInstanceOf(Throwable.class);
        // but: some actions have occurred
        verify(transport).start(any(McpOperationHandler.class));
        verify(transport).initialize(any());

        // when: onFailure callback is triggered
        assertThatCode(() -> {
                    onFailureCaptor.getValue().run();
                })
                .doesNotThrowAnyException();

        // then: no further actions should be taken
        verify(transport, times(1)).start(any(McpOperationHandler.class));
        verify(transport, times(1)).initialize(any());
        // and: transport should *not* be closed
        verify(transport, never()).close();
    }

    private static McpTransport getMinimalMcpTransportMock() {
        McpTransport transport = mock(McpTransport.class);
        ObjectNode emptyJsonNode = JsonNodeFactory.instance.objectNode();
        when(transport.initialize(any())).thenReturn(CompletableFuture.completedFuture(emptyJsonNode));
        return transport;
    }
}
