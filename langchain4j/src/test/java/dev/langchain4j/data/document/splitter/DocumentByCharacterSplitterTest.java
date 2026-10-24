package dev.langchain4j.data.document.splitter;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentByCharacterSplitterTest {

    // An emoji is a single code point stored as two UTF-16 chars, so it is the
    // smallest text that a code-unit-based split can cut in half.
    private static final String TEXT_WITH_EMOJI = "ab🙂cd🙂ef";

    private static boolean survivesUtf8(String text) {
        return new String(text.getBytes(UTF_8), UTF_8).equals(text);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6})
    void should_not_cut_a_code_point_in_half(int maxSegmentSizeInChars) {
        List<TextSegment> segments =
                new DocumentByCharacterSplitter(maxSegmentSizeInChars, 0).split(Document.from(TEXT_WITH_EMOJI));

        assertThat(segments).isNotEmpty();
        assertThat(segments)
                .allSatisfy(segment -> assertThat(survivesUtf8(segment.text()))
                        .as("segment %s is not valid UTF-8", segment.text())
                        .isTrue());
        assertThat(segments.stream().map(TextSegment::text).reduce("", String::concat))
                .isEqualTo(TEXT_WITH_EMOJI);
    }

    @Test
    void should_fail_loudly_when_a_single_code_point_exceeds_the_segment_size() {
        // A code point that cannot fit is the documented "no subSplitter" case;
        // emitting half of it instead would be silent corruption.
        assertThatThrownBy(() -> new DocumentByCharacterSplitter(1, 0).split(Document.from(TEXT_WITH_EMOJI)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("doesn't fit into the maximum segment size");
    }

    @Test
    void should_not_fail_when_a_token_count_estimator_sees_a_split_code_point() {
        TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator(GPT_3_5_TURBO);

        assertThatCode(() -> new DocumentByCharacterSplitter(6, 0, tokenCountEstimator)
                        .split(Document.from("Quarterly report 🙂 revenue up.")))
                .doesNotThrowAnyException();
    }
}
