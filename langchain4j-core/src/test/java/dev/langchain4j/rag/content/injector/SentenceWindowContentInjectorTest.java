package dev.langchain4j.rag.content.injector;

import static dev.langchain4j.data.segment.SentenceWindowTextSegmentTransformer.SURROUNDING_CONTEXT_KEY;
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
    void should_inject_surrounding_context_and_fall_back_to_segment_text() {
        Content context = Content.from(
                TextSegment.from("Target", Metadata.from(SURROUNDING_CONTEXT_KEY, "Before. Target. After.")));
        Content plain = Content.from("Plain text.");

        UserMessage result = (UserMessage)
                new SentenceWindowContentInjector().inject(List.of(context, plain), UserMessage.from("Question?"));

        assertThat(result.singleText()).isEqualTo("""
                Question?

                Answer using the following information:
                Before. Target. After.

                Plain text.\
                """);
    }

    @Test
    void should_support_existing_content_injector_options() {
        Metadata metadata =
                new Metadata().put(SURROUNDING_CONTEXT_KEY, "Expanded").put("source", "doc.txt");
        SentenceWindowContentInjector injector = SentenceWindowContentInjector.builder()
                .promptTemplate(PromptTemplate.from("{{userMessage}}\n{{contents}}"))
                .metadataKeysToInclude(List.of(SURROUNDING_CONTEXT_KEY, "source"))
                .build();

        UserMessage result = (UserMessage) injector.inject(
                List.of(Content.from(TextSegment.from("Target", metadata))), UserMessage.from("Question?"));

        assertThat(result.singleText()).isEqualTo("Question?\ncontent: Expanded\nsource: doc.txt");
    }
}
