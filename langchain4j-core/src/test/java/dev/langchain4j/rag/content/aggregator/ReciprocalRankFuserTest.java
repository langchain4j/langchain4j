package dev.langchain4j.rag.content.aggregator;

import static dev.langchain4j.rag.content.ContentMetadata.SCORE;
import static dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser.fuse;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReciprocalRankFuserTest {

    private static final Content A = Content.from("A");
    private static final Content B = Content.from("B");
    private static final Content C = Content.from("C");
    private static final Content D = Content.from("D");

    @ParameterizedTest
    @MethodSource
    void should_fuse(Collection<List<Content>> contents, List<Content> expected) {
        assertThat(fuse(contents)).isEqualTo(expected);
    }

    @Test
    void should_fuse_by_text_segment_when_content_metadata_differs() {
        TextSegment textSegment = TextSegment.from("same text");
        Content first = Content.from(textSegment, Map.of(SCORE, 0.9));
        Content second = Content.from(textSegment, Map.of(SCORE, 0.5));
        Content other = Content.from("other text");

        List<Content> fused = fuse(asList(asList(first, other), asList(second)));

        assertThat(fused).containsExactly(first, other);
    }

    public static Stream<Arguments> should_fuse() {
        return Stream.<Arguments>builder()

                // 0 & 0
                .add(Arguments.of(asList(list(), list()), list()))

                // 0 & 1
                .add(Arguments.of(asList(list(), list(A)), list(A)))

                // 1 & 0
                .add(Arguments.of(asList(list(A), list()), list(A)))

                // 1 & 1
                .add(Arguments.of(asList(list(A), list(A)), list(A)))
                .add(Arguments.of(asList(list(A), list(B)), asList(A, B)))

                // 1 & 2
                .add(Arguments.of(asList(list(A), list(A, B)), asList(A, B)))
                .add(Arguments.of(asList(list(A), list(B, A)), asList(A, B)))

                // 2 & 1
                .add(Arguments.of(asList(list(A, B), list(A)), asList(A, B)))
                .add(Arguments.of(asList(list(A, B), list(B)), asList(B, A)))

                // 2 & 2
                .add(Arguments.of(asList(list(A, B), list(A, B)), asList(A, B)))
                .add(Arguments.of(asList(list(A, B), list(B, A)), asList(A, B)))
                .add(Arguments.of(asList(list(A, B), list(A, C)), asList(A, B, C)))
                .add(Arguments.of(asList(list(A, B), list(B, C)), asList(B, A, C)))
                .add(Arguments.of(asList(list(A, B), list(C, D)), asList(A, C, B, D)))
                .build();
    }

    private static List<Content> list(Content... contents) {
        return asList(contents);
    }
}
