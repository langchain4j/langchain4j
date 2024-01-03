package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class DocumentTest implements WithAssertions {
    @Test
    public void test_equals_hashCode() {
        Document document1 = Document.from("foo bar");
        Document document2 = Document.from("foo bar");
        Document document3 = Document.from("foo bar", Metadata.from("foo", "bar"));
        Document document4 = Document.from("foo bar", Metadata.from("foo", "bar"));

        assertThat(document1)
                .isEqualTo(document2)
                .hasSameHashCodeAs(document2);

        assertThat(document1)
                .isNotEqualTo(document3)
                .doesNotHaveSameHashCodeAs(document3);

        assertThat(document3)
                .isEqualTo(document4)
                .hasSameHashCodeAs(document4);
    }
    @Test
    public void test_noMetadata() {

        Document document = Document.from("foo bar");
        assertThat(document.text()).isEqualTo("foo bar");
        assertThat(document.metadata().asMap().isEmpty()).isTrue();

        assertThat(document).hasToString("Document { text = \"foo bar\" metadata = {} }");

        Map<String, String> expectedMetadata = new HashMap<>();
        expectedMetadata.put("index", "0");
        assertThat(document.toTextSegment())
                .isEqualTo(new TextSegment("foo bar", new Metadata(expectedMetadata)));
    }

    @Test
    public void test_withMetadata() {
        Document document = Document.from("foo bar", Metadata.from("foo", "bar"));
        assertThat(document.text()).isEqualTo("foo bar");

        assertThat(document.metadata().asMap()).hasSize(1);
        assertThat(document.metadata("foo")).isEqualTo("bar");

        assertThat(document).hasToString("Document { text = \"foo bar\" metadata = {foo=bar} }");

        Map<String, String> expectedMetadata = new HashMap<>();
        expectedMetadata.put("index", "0");
        expectedMetadata.put("foo", "bar");
        assertThat(document.toTextSegment())
                .isEqualTo(new TextSegment("foo bar", new Metadata(expectedMetadata)));
    }

    @Test
    public void test_from() {
        assertThat(Document.from("foo bar"))
                .isEqualTo(new Document("foo bar", new Metadata()));

        assertThat(Document.from("foo bar", Metadata.from("foo", "bar")))
                .isEqualTo(new Document("foo bar", Metadata.from("foo", "bar")));

        assertThat(Document.document("foo bar"))
                .isEqualTo(new Document("foo bar", new Metadata()));
        assertThat(Document.document("foo bar", Metadata.from("foo", "bar")))
                .isEqualTo(new Document("foo bar", Metadata.from("foo", "bar")));
    }

}