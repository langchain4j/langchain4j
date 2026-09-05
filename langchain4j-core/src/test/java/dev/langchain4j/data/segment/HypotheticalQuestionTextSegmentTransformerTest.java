package dev.langchain4j.data.segment;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionTextSegmentTransformerTest {

    @Test
    void should_generate_question_segments() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(" Question 1? \n\nQuestion 2?\nQuestion 3?");
        TextSegment segment = TextSegment.from("Original text", Metadata.from("source", "document.txt"));
        TextSegmentTransformer transformer = new HypotheticalQuestionTextSegmentTransformer(chatModel, 2);

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result)
                .containsExactly(
                        TextSegment.from(
                                "Question 1?",
                                new Metadata()
                                        .put("source", "document.txt")
                                        .put(ORIGINAL_TEXT_METADATA_KEY, "Original text")),
                        TextSegment.from(
                                "Question 2?",
                                new Metadata()
                                        .put("source", "document.txt")
                                        .put(ORIGINAL_TEXT_METADATA_KEY, "Original text")));
        assertThat(chatModel.userMessageText()).contains("Generate 2 hypothetical questions", "Text: Original text");
        assertThat(segment.metadata().containsKey(ORIGINAL_TEXT_METADATA_KEY)).isFalse();
    }

    @Test
    void should_use_original_text_when_no_question_is_generated() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(AiMessage.from(" \n "));
        TextSegment segment = TextSegment.from("Original text");
        TextSegmentTransformer transformer = new HypotheticalQuestionTextSegmentTransformer(chatModel);

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result)
                .containsExactly(
                        TextSegment.from("Original text", Metadata.from(ORIGINAL_TEXT_METADATA_KEY, "Original text")));
    }

    @Test
    void should_reject_reserved_metadata_before_calling_chat_model() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Question?");
        TextSegment segment =
                TextSegment.from("Original text", Metadata.from(ORIGINAL_TEXT_METADATA_KEY, "existing value"));
        TextSegmentTransformer transformer = new HypotheticalQuestionTextSegmentTransformer(chatModel);

        // when / then
        assertThatThrownBy(() -> transformer.transformAll(List.of(segment)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ORIGINAL_TEXT_METADATA_KEY);
        assertThat(chatModel.requests()).isEmpty();
    }
}
