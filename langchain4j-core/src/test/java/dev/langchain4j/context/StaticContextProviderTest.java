package dev.langchain4j.context;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticContextProviderTest {

    @Test
    void should_provide_static_text_content() {
        StaticContextProvider provider = StaticContextProvider.of("policy 1", "policy 2");

        List<Content> contents = provider.provideContext(dummyRequest());

        assertThat(contents).hasSize(2);
        assertThat(contents.get(0).textSegment().text()).isEqualTo("policy 1");
        assertThat(contents.get(1).textSegment().text()).isEqualTo("policy 2");
    }

    @Test
    void should_provide_static_content_objects() {
        Content c1 = Content.from("content 1");
        Content c2 = Content.from("content 2");
        StaticContextProvider provider = StaticContextProvider.of(c1, c2);

        List<Content> contents = provider.provideContext(dummyRequest());

        assertThat(contents).containsExactly(c1, c2);
    }

    @Test
    void should_provide_static_content_from_list() {
        List<Content> input = List.of(Content.from("a"), Content.from("b"));
        StaticContextProvider provider = StaticContextProvider.of(input);

        List<Content> contents = provider.provideContext(dummyRequest());

        assertThat(contents).hasSize(2);
        assertThat(contents.get(0).textSegment().text()).isEqualTo("a");
    }

    @Test
    void should_return_same_content_regardless_of_request() {
        StaticContextProvider provider = StaticContextProvider.of("constant");

        List<Content> first = provider.provideContext(dummyRequest());
        List<Content> second = provider.provideContext(dummyRequest());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void should_use_builder_with_name() {
        StaticContextProvider provider = StaticContextProvider.builder()
                .name("policies")
                .content("rule 1")
                .content(Content.from("rule 2"))
                .build();

        assertThat(provider.name()).isEqualTo("policies");
        assertThat(provider.provideContext(dummyRequest())).hasSize(2);
    }

    @Test
    void should_use_default_name() {
        StaticContextProvider provider = StaticContextProvider.of("test");
        assertThat(provider.name()).isEqualTo("StaticContextProvider");
    }

    @Test
    void should_reject_empty_content() {
        assertThatThrownBy(() -> StaticContextProvider.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
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
