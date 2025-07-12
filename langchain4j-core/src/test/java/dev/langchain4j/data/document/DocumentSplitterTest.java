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

    @Test
    void split_all_varargs() {
        WhitespaceSplitter splitter = new WhitespaceSplitter();

        // Case 1: null varargs
        assertThat(splitter.splitAll((Document[]) null)).isEmpty();

        // Case 2: empty varargs
        assertThat(splitter.splitAll()).isEmpty();

        // Case 3: single document with default metadata
        Document doc1 = Document.document("hello world");
        List<TextSegment> result1 = splitter.splitAll(doc1);
        assertThat(result1)
                .containsExactly(new TextSegment("hello", new Metadata()), new TextSegment("world", new Metadata()));

        // Case 4: multiple documents with mixed metadata
        Document doc2 = Document.document("foo bar", Metadata.metadata("x", "1"));
        List<TextSegment> result2 = splitter.splitAll(doc1, doc2);
        assertThat(result2)
                .containsExactly(
                        new TextSegment("hello", new Metadata()),
                        new TextSegment("world", new Metadata()),
                        new TextSegment("foo", Metadata.metadata("x", "1")),
                        new TextSegment("bar", Metadata.metadata("x", "1")));
    }
}
