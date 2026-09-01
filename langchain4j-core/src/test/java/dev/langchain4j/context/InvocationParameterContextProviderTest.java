package dev.langchain4j.context;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvocationParameterContextProviderTest {

    @Test
    void should_extract_string_value_as_content() {
        InvocationParameterContextProvider provider = InvocationParameterContextProvider.of("profile");
        InvocationParameters params = InvocationParameters.from("profile", "Role: admin");

        List<Content> contents = provider.provideContext(requestWithParams(params));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).textSegment().text()).isEqualTo("Role: admin");
    }

    @Test
    void should_extract_content_value_directly() {
        InvocationParameterContextProvider provider = InvocationParameterContextProvider.of("ctx");
        Content content = Content.from("pre-built content");
        InvocationParameters params = InvocationParameters.from("ctx", content);

        List<Content> contents = provider.provideContext(requestWithParams(params));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).isEqualTo(content);
    }

    @Test
    void should_extract_list_of_content_values() {
        InvocationParameterContextProvider provider = InvocationParameterContextProvider.of("items");
        List<Content> input = List.of(Content.from("a"), Content.from("b"));
        InvocationParameters params = InvocationParameters.from("items", input);

        List<Content> contents = provider.provideContext(requestWithParams(params));

        assertThat(contents).hasSize(2);
        assertThat(contents.get(0).textSegment().text()).isEqualTo("a");
        assertThat(contents.get(1).textSegment().text()).isEqualTo("b");
    }

    @Test
    void should_convert_other_types_via_toString() {
        InvocationParameterContextProvider provider = InvocationParameterContextProvider.of("num");
        InvocationParameters params = InvocationParameters.from("num", 42);

        List<Content> contents = provider.provideContext(requestWithParams(params));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).textSegment().text()).isEqualTo("42");
    }

    @Test
    void should_return_empty_when_key_not_present() {
        InvocationParameterContextProvider provider = InvocationParameterContextProvider.of("missing");
        InvocationParameters params = new InvocationParameters();

        List<Content> contents = provider.provideContext(requestWithParams(params));

        assertThat(contents).isEmpty();
    }

    @Test
    void should_return_empty_when_value_is_null() {
        InvocationParameterContextProvider provider = InvocationParameterContextProvider.of("key");
        InvocationParameters params = new InvocationParameters();

        List<Content> contents = provider.provideContext(requestWithParams(params));

        assertThat(contents).isEmpty();
    }

    @Test
    void should_have_descriptive_name() {
        InvocationParameterContextProvider provider = InvocationParameterContextProvider.of("userProfile");
        assertThat(provider.name()).isEqualTo("InvocationParameterContextProvider[userProfile]");
    }

    private static ContextRequest requestWithParams(InvocationParameters params) {
        UserMessage msg = UserMessage.from("test");
        Metadata metadata = Metadata.builder()
                .chatMessage(msg)
                .invocationContext(InvocationContext.builder()
                        .invocationParameters(params)
                        .build())
                .build();
        return new ContextRequest(msg, metadata);
    }
}
