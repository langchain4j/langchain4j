package dev.langchain4j.data.document.parser.tesseract;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class TesseractOcrDocumentParserTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "test-file.png",
            "test-file.jpg",
            "test-file.jpeg"
    })
    void should_parse_png_jpg_and_jpeg(String fileName) {

        DocumentParser parser = new TesseractOcrDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        Document document = parser.parse(inputStream);
        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test-file.png",
            "test-file.jpg",
            "test-file.jpeg"
    })
    void should_throw_Error(String fileName) {

        DocumentParser parser = new TesseractOcrDocumentParser("chn");
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        assertThatThrownBy(() -> parser.parse(inputStream))
                .isExactlyInstanceOf(Error.class);
    }
}
