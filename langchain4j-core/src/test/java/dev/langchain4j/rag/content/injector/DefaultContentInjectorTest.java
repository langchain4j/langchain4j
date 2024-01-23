package dev.langchain4j.rag.content.injector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultContentInjectorTest {

    @Test
    void should_not_inject_when_no_content() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        List<Content> contents = emptyList();

        ContentInjector injector = new DefaultContentInjector();

        // when
        UserMessage injected = injector.inject(contents, userMessage);

        // then
        assertThat(injected).isEqualTo(userMessage);
    }

    @Test
    void should_inject_single_content() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        List<Content> contents = singletonList(Content.from("Bananas are awesome!"));

        ContentInjector injector = new DefaultContentInjector();

        // when
        UserMessage injected = injector.inject(contents, userMessage);

        // then
        assertThat(injected.text()).isEqualTo(
                "Tell me about bananas.\n" +
                        "\n" +
                        "Answer using the following information:\n" +
                        "Bananas are awesome!"
        );
    }

    @Test
    void should_inject_single_content_with_metadata() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        TextSegment segment = TextSegment.from(
                "Bananas are awesome!",
                Metadata.from("source", "trust me bro")
        );
        List<Content> contents = singletonList(Content.from(segment));

        List<String> metadataKeysToInclude = singletonList("source");

        ContentInjector injector = new DefaultContentInjector(metadataKeysToInclude);

        // when
        UserMessage injected = injector.inject(contents, userMessage);

        // then
        assertThat(injected.text()).isEqualTo(
                "Tell me about bananas.\n" +
                        "\n" +
                        "Answer using the following information:\n" +
                        "content: Bananas are awesome!\n" +
                        "source: trust me bro"
        );
    }

    @Test
    void should_inject_multiple_contents() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        List<Content> contents = asList(
                Content.from("Bananas are awesome!"),
                Content.from("Bananas are healthy!")
        );

        ContentInjector injector = new DefaultContentInjector();

        // when
        UserMessage injected = injector.inject(contents, userMessage);

        // then
        assertThat(injected.text()).isEqualTo(
                "Tell me about bananas.\n" +
                        "\n" +
                        "Answer using the following information:\n" +
                        "Bananas are awesome!\n" +
                        "\n" +
                        "Bananas are healthy!"
        );
    }

    @Test
    void should_inject_multiple_contents_with_multiple_metadatas() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        TextSegment segment1 = TextSegment.from(
                "Bananas are awesome!",
                Metadata.from("source", "trust me bro")
                        .add("date", "today")
        );
        TextSegment segment2 = TextSegment.from(
                "Bananas are healthy!",
                Metadata.from("source", "my doctor")
                        .add("reliability", "100%")
        );
        List<Content> contents = asList(Content.from(segment1), Content.from(segment2));

        List<String> metadataKeysToInclude = asList("source", "reliability", "date");

        ContentInjector injector = new DefaultContentInjector(metadataKeysToInclude);

        // when
        UserMessage injected = injector.inject(contents, userMessage);

        // then
        assertThat(injected.text()).isEqualTo(
                "Tell me about bananas.\n" +
                        "\n" +
                        "Answer using the following information:\n" +
                        "content: Bananas are awesome!\n" +
                        "source: trust me bro\n" +
                        "date: today\n" +
                        "\n" +
                        "content: Bananas are healthy!\n" +
                        "source: my doctor\n" +
                        "reliability: 100%"
        );
    }

    @Test
    void should_inject_multiple_contents_with_custom_prompt_template() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("{{userMessage}}\n{{contents}}");

        UserMessage userMessage = UserMessage.from("Tell me about bananas.");
        List<Content> contents = asList(
                Content.from("Bananas are awesome!"),
                Content.from("Bananas are healthy!")
        );
        ContentInjector injector = new DefaultContentInjector(promptTemplate);

        // when
        UserMessage injected = injector.inject(contents, userMessage);

        // then
        assertThat(injected.text()).isEqualTo(
                "Tell me about bananas.\n" +
                        "Bananas are awesome!\n" +
                        "\n" +
                        "Bananas are healthy!"
        );
    }
}