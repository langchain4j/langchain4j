package dev.langchain4j.data.document.parser.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.InputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;

public class YamlDocumentParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"test1.yaml", "test3.yaml", "test4.yaml", "test5.yaml"})
    void should_parse_yaml_files(String fileName) {

        DocumentParser parser = new YamlDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isNotBlank();
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test2.yaml"})
    void should_throw_DuplicateKeyException(String fileName) {

        DocumentParser parser = new YamlDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        assertThatThrownBy(() -> parser.parse(inputStream)).isExactlyInstanceOf(DuplicateKeyException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"test6.yaml"})
    void should_throw_BlankDocumentException(String fileName) {

        DocumentParser parser = new YamlDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        assertThatThrownBy(() -> parser.parse(inputStream)).isExactlyInstanceOf(BlankDocumentException.class);
    }
}
