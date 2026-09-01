package dev.langchain4j.context;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultContextManagerTest {

    @Test
    void should_compose_multiple_providers() {
        ContextProvider provider1 = request -> List.of(Content.from("from provider 1"));
        ContextProvider provider2 = request -> List.of(Content.from("from provider 2"));

        DefaultContextManager manager = DefaultContextManager.builder()
                .contextProvider(provider1)
                .contextProvider(provider2)
                .build();

        ContextResult result = manager.resolveContext(dummyRequest());

        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("from provider 1");
        assertThat(result.contents().get(1).textSegment().text()).isEqualTo("from provider 2");
        assertThat(result.isRetrievalAdvised()).isTrue();
    }

    @Test
    void should_skip_failing_provider_and_continue() {
        ContextProvider failing = request -> {
            throw new RuntimeException("boom");
        };
        ContextProvider working = request -> List.of(Content.from("success"));

        DefaultContextManager manager = DefaultContextManager.builder()
                .contextProvider(failing)
                .contextProvider(working)
                .build();

        ContextResult result = manager.resolveContext(dummyRequest());

        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("success");
    }

    @Test
    void should_handle_provider_returning_null() {
        ContextProvider nullProvider = request -> null;
        ContextProvider normalProvider = request -> List.of(Content.from("ok"));

        DefaultContextManager manager = DefaultContextManager.builder()
                .contextProvider(nullProvider)
                .contextProvider(normalProvider)
                .build();

        ContextResult result = manager.resolveContext(dummyRequest());

        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("ok");
    }

    @Test
    void should_handle_provider_returning_empty_list() {
        ContextProvider emptyProvider = request -> List.of();
        ContextProvider normalProvider = request -> List.of(Content.from("data"));

        DefaultContextManager manager = DefaultContextManager.builder()
                .contextProvider(emptyProvider)
                .contextProvider(normalProvider)
                .build();

        ContextResult result = manager.resolveContext(dummyRequest());

        assertThat(result.contents()).hasSize(1);
    }

    @Test
    void should_work_with_parallel_execution() {
        ContextProvider provider1 = request -> List.of(Content.from("parallel 1"));
        ContextProvider provider2 = request -> List.of(Content.from("parallel 2"));

        DefaultContextManager manager = DefaultContextManager.builder()
                .contextProvider(provider1)
                .contextProvider(provider2)
                .executor(Executors.newFixedThreadPool(2))
                .build();

        ContextResult result = manager.resolveContext(dummyRequest());

        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents().stream()
                .map(c -> c.textSegment().text())
                .toList())
                .containsExactly("parallel 1", "parallel 2");
    }

    @Test
    void should_handle_failing_provider_in_parallel_mode() {
        ContextProvider failing = request -> {
            throw new RuntimeException("parallel fail");
        };
        ContextProvider working = request -> List.of(Content.from("parallel ok"));

        DefaultContextManager manager = DefaultContextManager.builder()
                .contextProvider(failing)
                .contextProvider(working)
                .executor(Executors.newFixedThreadPool(2))
                .build();

        ContextResult result = manager.resolveContext(dummyRequest());

        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("parallel ok");
    }

    @Test
    void should_accept_providers_via_list() {
        List<ContextProvider> providers = List.of(
                request -> List.of(Content.from("a")),
                request -> List.of(Content.from("b")));

        DefaultContextManager manager = DefaultContextManager.builder()
                .contextProviders(providers)
                .build();

        ContextResult result = manager.resolveContext(dummyRequest());

        assertThat(result.contents()).hasSize(2);
    }

    private static ContextRequest dummyRequest() {
        UserMessage msg = UserMessage.from("test");
        Metadata metadata = Metadata.builder()
                .chatMessage(msg)
                .invocationContext(InvocationContext.builder()
                        .invocationParameters(new InvocationParameters())
                        .build())
                .build();
        return new ContextRequest(msg, metadata);
    }
}
