package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiFilesIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void test_file_upload_get_list_delete() throws Exception {
        GoogleGenAiFiles files =
                GoogleGenAiFiles.builder().apiKey(GOOGLE_AI_GEMINI_API_KEY).build();

        Path tempFile = Files.createTempFile("test-gemini", ".txt");
        Files.writeString(tempFile, "Hello World Gemini File API");

        try {
            File file = files.uploadFile(tempFile, "My Test File");
            assertThat(file).isNotNull();
            assertThat(file.name().isPresent()).isTrue();

            String fileName = file.name().get();

            File metadata = files.getMetadata(fileName);
            assertThat(metadata.displayName().orElse("")).isEqualTo("My Test File");

            List<File> allFiles = files.listFiles();
            assertThat(allFiles).isNotEmpty();
            boolean found = allFiles.stream()
                    .anyMatch(f -> f.name().isPresent() && f.name().get().equals(fileName));
            assertThat(found).isTrue();

            files.deleteFile(fileName);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
