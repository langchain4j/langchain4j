package dev.langchain4j.data.document.parser.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.InputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class MarkdownDocumentParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"test1.md", "test2.md", "test3.md", "test4.md", "test5.md"})
    void should_parse_markdown_files(String fileName) {

        DocumentParser parser = new MarkdownDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isNotBlank();
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test6.md"})
    void should_throw_BlankDocumentException(String fileName) {

        DocumentParser parser = new MarkdownDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        assertThatThrownBy(() -> parser.parse(inputStream)).isExactlyInstanceOf(BlankDocumentException.class);
    }
}
