package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DocumentTest implements WithAssertions {

    @Test
    void test_equals_hashCode() {
        final var document1 = Document.from("foo bar");
        final var document2 = Document.from("foo bar");
        final var document3 = Document.from("foo bar", Metadata.from("foo", "bar"));
        final var document4 = Document.from("foo bar", Metadata.from("foo", "bar"));

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
    void test_noMetadata() {

        final var document = Document.from("foo bar");
        assertThat(document.text()).isEqualTo("foo bar");
        assertThat(document.metadata().asMap()).isEmpty();
        assertThat(document.metadata().toMap()).isEmpty();

        assertThat(document).hasToString("DefaultDocument[text=foo bar, metadata=Metadata { metadata = {} }]");

        final var expectedMetadata = new HashMap<String, Object>();
        expectedMetadata.put("index", "0");
        assertThat(document.toTextSegment())
                .isEqualTo(new TextSegment("foo bar", Metadata.from(expectedMetadata)));
    }

    @Test
    void test_withMetadata() {
        final var document = Document.from("foo bar", Metadata.from("foo", "bar"));
        assertThat(document.text()).isEqualTo("foo bar");

        assertThat(document.metadata().asMap()).hasSize(1);
        assertThat(document.metadata().toMap()).hasSize(1);
        assertThat(document.metadata("foo")).isEqualTo("bar");
        assertThat(document.metadata().getString("foo")).isEqualTo("bar");

        assertThat(document).hasToString("DefaultDocument[text=foo bar, metadata=Metadata { metadata = {foo=bar} }]");

        final var expectedMetadata = new HashMap<String, Object>();
        expectedMetadata.put("index", "0");
        expectedMetadata.put("foo", "bar");
        assertThat(document.toTextSegment())
                .isEqualTo(new TextSegment("foo bar", Metadata.from(expectedMetadata)));
    }

    @Test
    void test_from() {
        assertThat(Document.from("foo bar"))
                .isEqualTo(Document.from("foo bar", new Metadata()));

        assertThat(Document.from("foo bar", Metadata.from("foo", "bar")))
                .isEqualTo(Document.from("foo bar", Metadata.from("foo", "bar")));

        assertThat(Document.document("foo bar"))
                .isEqualTo(Document.from("foo bar", new Metadata()));
        assertThat(Document.document("foo bar", Metadata.from("foo", "bar")))
                .isEqualTo(Document.from("foo bar", Metadata.from("foo", "bar")));
    }

    @ParameterizedTest(name="{index}: \"{arguments}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @NullSource
    void constructor_should_fail_on_empty_text(String text) {
        final var exception = assertThrows(IllegalArgumentException.class,
                () -> Document.from(text)
        );
        assertThat(exception).hasMessage("text cannot be null or blank");

        final var exception2 = assertThrows(IllegalArgumentException.class,
                () -> Document.from(text, mock(Metadata.class))
        );
        assertThat(exception2).hasMessage("text cannot be null or blank");
    }

    @Test
    void constructor_should_fail_on_empty_metadata() {
        final var exception = assertThrows(IllegalArgumentException.class,
                () -> Document.from("ok", null)
        );
        assertThat(exception).hasMessage("metadata cannot be null");
    }

}
