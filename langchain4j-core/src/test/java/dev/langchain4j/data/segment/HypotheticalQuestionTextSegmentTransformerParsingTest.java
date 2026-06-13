package dev.langchain4j.data.segment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionTextSegmentTransformerParsingTest {

    @Test
    void should_filter_blank_lines_from_response() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Question 1?\n\n\nQuestion 2?\n\n");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .build();

        List<TextSegment> result = transformer.transformAll(List.of(TextSegment.from("Some text.")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("Question 1?");
        assertThat(result.get(1).text()).isEqualTo("Question 2?");
    }

    @Test
    void should_strip_common_list_markers_from_generated_questions() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("1. Question 1?\n- Question 2?\n* Question 3?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(3)
                .build();

        List<TextSegment> result = transformer.transformAll(List.of(TextSegment.from("Some text.")));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).text()).isEqualTo("Question 1?");
        assertThat(result.get(1).text()).isEqualTo("Question 2?");
        assertThat(result.get(2).text()).isEqualTo("Question 3?");
    }

    @Test
    void should_limit_questions_to_requested_number() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Q1?\nQ2?\nQ3?\nQ4?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(2)
                .build();

        List<TextSegment> result = transformer.transformAll(List.of(TextSegment.from("Some text.")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("Q1?");
        assertThat(result.get(1).text()).isEqualTo("Q2?");
    }

    @Test
    void should_trim_whitespace_from_generated_questions() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("  Question 1?  \n  Question 2?  ");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .build();

        List<TextSegment> result = transformer.transformAll(List.of(TextSegment.from("Some text.")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("Question 1?");
        assertThat(result.get(1).text()).isEqualTo("Question 2?");
    }
}
