package dev.langchain4j.data.segment;

import java.util.List;

public interface TextSegmentTransformer {

    List<TextSegment> transform(List<TextSegment> segments);
}
