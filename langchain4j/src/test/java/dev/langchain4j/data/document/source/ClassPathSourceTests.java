package dev.langchain4j.data.document.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClassPathSourceTests {
    @Test
    void doesntExist() {
        assertThatThrownBy(() -> ClassPathSource.from("resourceShouldntExist/because/it/just/shouldnt.txt"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "'resourceShouldntExist/because/it/just/shouldnt.txt' was not found as a classpath resource");
    }

    @Disabled("TODO fix")
    @ParameterizedTest
    @ValueSource(
            strings = {
                "classPathSourceTests/file1.txt",
                "classPathSourceTests/anotherDir/file2.txt",
                "classPathSourceTestsInJar/file3.txt",
                "classPathSourceTestsInJar/folderInsideJar/file4.txt"
            })
    void findFile(String classPathResource) throws IOException {
        var classPathSource = ClassPathSource.from(classPathResource);
        var urlString = classPathSource.url().getFile();
        var filename = urlString.substring(urlString.lastIndexOf(File.separatorChar) + 1);
        var expectedMetaData = new Metadata()
                .put(Document.URL, urlString)
                .put(Document.FILE_NAME, urlString.substring(urlString.lastIndexOf(File.separatorChar) + 1));

        assertThat(classPathSource)
                .isNotNull()
                .extracting(ClassPathSource::metadata)
                .isEqualTo(expectedMetaData);

        assertThat(new String(classPathSource.inputStream().readAllBytes()))
                .isEqualTo("This is %s\n".formatted(filename));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
      classPathSourceTests                               | false
      classPathSourceTests/file1.txt                      | false
      classPathSourceTests/anotherDir                    | false
      classPathSourceTests/anotherDir/file2.txt           | false
      classPathSourceTestsInJar                          | true
      classPathSourceTestsInJar/file3.txt                 | true
      classPathSourceTestsInJar/folderInsideJar          | true
      classPathSourceTestsInJar/folderInsideJar/file4.txt | true
      """)
    void isInsideArchive(String classPathResource, boolean shouldBeInsideArchive) {
        var classPathSource = ClassPathSource.from(classPathResource);

        assertThat(classPathSource)
                .isNotNull()
                .extracting(ClassPathSource::isInsideArchive)
                .isEqualTo(shouldBeInsideArchive);
    }
}
