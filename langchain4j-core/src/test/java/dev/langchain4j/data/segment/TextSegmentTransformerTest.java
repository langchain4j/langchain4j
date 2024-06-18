package dev.langchain4j.data.segment;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class TextSegmentTransformerTest implements WithAssertions {
    public static class LowercaseFnordTransformer implements TextSegmentTransformer {
        @Override
        public TextSegment transform(TextSegment segment) {
            String result = segment.text().toLowerCase();
            if (result.contains("fnord")) {
                return null;
            }
            return TextSegment.from(result, segment.metadata());
        }
    }

    @Test
    public void test_transformAll() {
        TextSegmentTransformer transformer = new LowercaseFnordTransformer();
        TextSegment ts1 = TextSegment.from("Text");
        ts1.metadata().put("abc", "123"); // metadata is copied over (not transformed

        TextSegment ts2 = TextSegment.from("Segment");
        TextSegment ts3 = TextSegment.from("Fnord will be filtered out");
        TextSegment ts4 = TextSegment.from("Transformer");

        List<TextSegment> segmentList = new ArrayList<>();
        segmentList.add(ts1);
        segmentList.add(ts2);
        segmentList.add(ts3);
        segmentList.add(ts4);

        assertThat(transformer.transformAll(segmentList))
                .containsExactly(
                        TextSegment.from("text", ts1.metadata()),
                        TextSegment.from("segment"),
                        TextSegment.from("transformer"));

    }

}