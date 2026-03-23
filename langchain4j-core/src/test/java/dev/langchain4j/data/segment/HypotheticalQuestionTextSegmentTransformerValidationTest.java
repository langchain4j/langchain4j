package dev.langchain4j.data.segment;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.DEFAULT_NUMBER_OF_QUESTIONS;
import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionTextSegmentTransformerValidationTest {

    @Test
    void should_return_empty_list_for_empty_input() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("irrelevant");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .build();

        List<TextSegment> result = transformer.transformAll(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_for_null_input() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("irrelevant");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .build();

        List<TextSegment> result = transformer.transformAll((List<TextSegment>) null);

        assertThat(result).isEmpty();
    }

    @Test
    void should_use_default_number_of_questions() {
        assertThat(DEFAULT_NUMBER_OF_QUESTIONS).isEqualTo(3);
    }

    @Test
    void should_throw_when_transform_called_directly() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("irrelevant");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .build();

        assertThatThrownBy(() -> transformer.transform(TextSegment.from("Some text.")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("1:N");
    }

    @Test
    void should_reject_null_chat_model() {
        assertThatThrownBy(() ->
                        HypotheticalQuestionTextSegmentTransformer.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_reserved_original_text_metadata_key() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("What is Java?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .build();

        TextSegment segment =
                TextSegment.from("Java is a programming language.", Metadata.from(ORIGINAL_TEXT_METADATA_KEY, "user"));

        assertThatThrownBy(() -> transformer.transformAll(List.of(segment)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ORIGINAL_TEXT_METADATA_KEY);
    }
}
