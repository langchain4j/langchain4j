package dev.langchain4j.rag.content.injector;

import static dev.langchain4j.data.segment.SentenceWindowTextSegmentTransformer.SURROUNDING_CONTEXT_KEY;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentenceWindowContentInjectorTest {

    @Test
    void should_inject_surrounding_context_from_metadata() {

        // given
        UserMessage userMessage = UserMessage.from("What is this about?");

        Metadata metadata = new Metadata().put(SURROUNDING_CONTEXT_KEY, "Before. Target. After.");
        TextSegment segment = TextSegment.from("Target.", metadata);
        List<Content> contents = singletonList(Content.from(segment));

        ContentInjector injector = SentenceWindowContentInjector.builder().build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
            What is this about?

            Answer using the following information:
            Before. Target. After.\
            """);
    }

    @Test
    void should_inject_surrounding_context_with_userName() {

        // given
        UserMessage userMessage = UserMessage.from("ape", "What is this about?");

        Metadata metadata = new Metadata().put(SURROUNDING_CONTEXT_KEY, "Before. Target. After.");
        List<Content> contents = singletonList(Content.from(TextSegment.from("Target.", metadata)));

        ContentInjector injector = SentenceWindowContentInjector.builder().build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
            What is this about?

            Answer using the following information:
            Before. Target. After.\
            """);
        assertThat(injected.name()).isEqualTo("ape");
    }

    @Test
    void should_fallback_to_segment_text_when_no_surrounding_context() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me more.");

        List<Content> contents = singletonList(Content.from("Original text."));

        ContentInjector injector = SentenceWindowContentInjector.builder().build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
            Tell me more.

            Answer using the following information:
            Original text.\
            """);
    }

    @Test
    void should_not_inject_when_no_content() {

        // given
        UserMessage userMessage = UserMessage.from("Hello.");

        List<Content> contents = emptyList();

        ContentInjector injector = SentenceWindowContentInjector.builder().build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected).isSameAs(userMessage);
    }

    @Test
    void should_inject_multiple_contents() {

        // given
        UserMessage userMessage = UserMessage.from("Question?");

        Metadata meta1 = new Metadata().put(SURROUNDING_CONTEXT_KEY, "Context A expanded.");
        Metadata meta2 = new Metadata().put(SURROUNDING_CONTEXT_KEY, "Context B expanded.");
        List<Content> contents =
                asList(Content.from(TextSegment.from("A.", meta1)), Content.from(TextSegment.from("B.", meta2)));

        ContentInjector injector = SentenceWindowContentInjector.builder().build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
            Question?

            Answer using the following information:
            Context A expanded.

            Context B expanded.\
            """);
    }

    @Test
    void should_inject_with_custom_prompt_template() {

        // given
        PromptTemplate customTemplate = PromptTemplate.from("{{userMessage}}\n{{contents}}");

        UserMessage userMessage = UserMessage.from("What?");

        Metadata metadata = new Metadata().put(SURROUNDING_CONTEXT_KEY, "Expanded context here.");
        List<Content> contents = singletonList(Content.from(TextSegment.from("Short.", metadata)));

        ContentInjector injector = SentenceWindowContentInjector.builder()
                .promptTemplate(customTemplate)
                .build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("What?\nExpanded context here.");
    }

    @Test
    void should_mix_segments_with_and_without_surrounding_context() {

        // given
        UserMessage userMessage = UserMessage.from("Ask.");

        Metadata metaWithContext = new Metadata().put(SURROUNDING_CONTEXT_KEY, "Wide context.");
        List<Content> contents = asList(
                Content.from(TextSegment.from("Narrow.", metaWithContext)),
                Content.from(TextSegment.from("Plain text.")));

        ContentInjector injector = SentenceWindowContentInjector.builder().build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
            Ask.

            Answer using the following information:
            Wide context.

            Plain text.\
            """);
    }

    @Test
    void should_inject_surrounding_context_with_included_metadata() {

        // given
        UserMessage userMessage = UserMessage.from("Question?");

        Metadata metadata = new Metadata()
                .put(SURROUNDING_CONTEXT_KEY, "Before. Target. After.")
                .put("source", "doc.txt");
        List<Content> contents = singletonList(Content.from(TextSegment.from("Target.", metadata)));

        ContentInjector injector = SentenceWindowContentInjector.builder()
                .metadataKeysToInclude(singletonList("source"))
                .build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
            Question?

            Answer using the following information:
            content: Before. Target. After.
            source: doc.txt\
            """);
    }

    @Test
    void should_not_duplicate_surrounding_context_when_metadata_keys_include_it() {

        // given
        UserMessage userMessage = UserMessage.from("Question?");

        Metadata metadata = new Metadata()
                .put(SURROUNDING_CONTEXT_KEY, "Before. Target. After.")
                .put("source", "doc.txt");
        List<Content> contents = singletonList(Content.from(TextSegment.from("Target.", metadata)));

        ContentInjector injector = SentenceWindowContentInjector.builder()
                .metadataKeysToInclude(asList(SURROUNDING_CONTEXT_KEY, "source"))
                .build();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
            Question?

            Answer using the following information:
            content: Before. Target. After.
            source: doc.txt\
            """);
    }
}
