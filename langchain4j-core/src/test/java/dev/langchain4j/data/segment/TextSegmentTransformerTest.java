package dev.langchain4j.data.segment;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

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
    void transform_all() {
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

    @Test
    void transform_all_varargs() {
        TextSegmentTransformer transformer = new LowercaseFnordTransformer();

        TextSegment ts1 = TextSegment.from("Text");
        ts1.metadata().put("abc", "123"); // metadata is copied over (not transformed)

        TextSegment ts2 = TextSegment.from("Segment");
        TextSegment ts3 = TextSegment.from("Fnord will be filtered out");
        TextSegment ts4 = TextSegment.from("Transformer");

        List<TextSegment> result = transformer.transformAll(ts1, ts2, ts3, ts4);

        assertThat(result)
                .containsExactly(
                        TextSegment.from("text", ts1.metadata()),
                        TextSegment.from("segment"),
                        TextSegment.from("transformer"));
    }

    @Test
    void transform_all_varargs_empty_input_returns_empty_list() {
        TextSegmentTransformer transformer = new LowercaseFnordTransformer();

        List<TextSegment> result = transformer.transformAll();

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void transform_all_varargs_null_input_returns_empty_list() {
        TextSegmentTransformer transformer = new LowercaseFnordTransformer();

        List<TextSegment> result = transformer.transformAll((TextSegment[]) null);

        assertThat(result).isNotNull().isEmpty();
    }
}
