package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class DocumentSplitterTest implements WithAssertions {
    public static final class WhitespaceSplitter implements DocumentSplitter {
        @Override
        public List<TextSegment> split(Document document) {
            Metadata metadata = document.metadata();
            return Arrays.stream(document.text().split("\\s+"))
                    .filter(s -> !s.isEmpty())
                    .map(s -> new TextSegment(s, metadata.copy()))
                    .collect(Collectors.toList());
        }
    }

    @Test
    void split_all() {
        List<Document> docs = new ArrayList<>();
        docs.add(Document.document("abc def"));
        docs.add(Document.document("abc def", Metadata.metadata("foo", "bar")));

        WhitespaceSplitter splitter = new WhitespaceSplitter();
        assertThat(splitter.splitAll(docs))
                .containsExactly(
                        new TextSegment("abc", new Metadata()),
                        new TextSegment("def", new Metadata()),
                        new TextSegment("abc", Metadata.metadata("foo", "bar")),
                        new TextSegment("def", Metadata.metadata("foo", "bar")));
    }
}
