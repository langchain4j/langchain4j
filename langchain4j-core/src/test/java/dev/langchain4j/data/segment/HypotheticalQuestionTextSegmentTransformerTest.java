package dev.langchain4j.data.segment;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionTextSegmentTransformerTest {

    @Test
    void should_generate_hypothetical_questions_for_single_segment() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                "What is photosynthesis?\nHow do plants make food?\nWhy do plants need sunlight?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .build();

        TextSegment segment = TextSegment.from("Plants convert sunlight into energy through photosynthesis.");

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).text()).isEqualTo("What is photosynthesis?");
        assertThat(result.get(1).text()).isEqualTo("How do plants make food?");
        assertThat(result.get(2).text()).isEqualTo("Why do plants need sunlight?");
    }

    @Test
    void should_store_original_text_in_metadata() {

        // given
        String originalText = "The Earth orbits the Sun.";
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("What orbits the Sun?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(1)
                .build();

        TextSegment segment = TextSegment.from(originalText);

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isEqualTo(originalText);
    }

    @Test
    void should_preserve_original_metadata() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("What is Java?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(1)
                .build();

        Metadata meta = new Metadata()
                .put("file_name", "doc.txt")
                .put("author", "Alice")
                .put("index", "0");
        TextSegment segment = TextSegment.from("Java is a programming language.", meta);

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).metadata().getString("file_name")).isEqualTo("doc.txt");
        assertThat(result.get(0).metadata().getString("author")).isEqualTo("Alice");
        assertThat(result.get(0).metadata().getString("index")).isEqualTo("0");
        assertThat(result.get(0).metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isEqualTo("Java is a programming language.");
    }

    @Test
    void should_not_mutate_original_segment_metadata() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Question 1?\nQuestion 2?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(2)
                .build();

        Metadata originalMeta = Metadata.from("file_name", "doc.txt");
        TextSegment segment = TextSegment.from("Some text.", originalMeta);

        // when
        transformer.transformAll(List.of(segment));

        // then
        assertThat(segment.metadata().getString(ORIGINAL_TEXT_METADATA_KEY)).isNull();
        assertThat(segment.metadata().getString("file_name")).isEqualTo("doc.txt");
    }

    @Test
    void should_handle_multiple_segments() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Q1?\nQ2?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(2)
                .build();

        TextSegment s0 = TextSegment.from("First segment.");
        TextSegment s1 = TextSegment.from("Second segment.");

        // when
        List<TextSegment> result = transformer.transformAll(List.of(s0, s1));

        // then
        assertThat(result).hasSize(4);
        assertThat(result.get(0).metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isEqualTo("First segment.");
        assertThat(result.get(1).metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isEqualTo("First segment.");
        assertThat(result.get(2).metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isEqualTo("Second segment.");
        assertThat(result.get(3).metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isEqualTo("Second segment.");
    }

    @Test
    void should_handle_fewer_questions_than_requested() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Only one question?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(3)
                .build();

        TextSegment segment = TextSegment.from("Some text.");

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("Only one question?");
    }

    @Test
    void should_fallback_to_original_text_when_chatmodel_returns_only_whitespace() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatResponds(request -> AiMessage.from("  \n  \n  "));

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(3)
                .build();

        TextSegment segment = TextSegment.from("Some text.", Metadata.from("file_name", "doc.txt"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("Some text.");
        assertThat(result.get(0).metadata().getString("file_name")).isEqualTo("doc.txt");
        assertThat(result.get(0).metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isEqualTo("Some text.");
    }

    @Test
    void should_use_custom_prompt_template() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Custom question?");

        PromptTemplate customTemplate = PromptTemplate.from("Generate {{numberOfQuestions}} questions for: {{text}}");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .promptTemplate(customTemplate)
                .numberOfQuestions(1)
                .build();

        TextSegment segment = TextSegment.from("Some text.");

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("Custom question?");
    }

    @Test
    void should_use_custom_number_of_questions() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Q1?\nQ2?\nQ3?\nQ4?\nQ5?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(5)
                .build();

        TextSegment segment = TextSegment.from("Some text.");

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(5);
    }

    @Test
    void should_not_share_metadata_between_question_segments() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Q1?\nQ2?");

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(2)
                .build();

        TextSegment segment = TextSegment.from("Some text.", Metadata.from("key", "value"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        result.get(0).metadata().put("extra", "data");
        assertThat(result.get(1).metadata().getString("extra")).isNull();
    }
}
