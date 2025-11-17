package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeminiFilesIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @TempDir
    Path tempDir;

    @Nested
    class UploadFileTest {
        @Test
        void should_uploadTextFile() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("test.txt", "Hello, Gemini! This is a test file.");

            // When
            GeminiFile uploadedFile = files.uploadFile(textFile, "Test Text File");

            // Then
            assertThat(uploadedFile).isNotNull();
            assertThat(uploadedFile.uri()).isNotEmpty();
            assertThat(uploadedFile.displayName()).isEqualTo("Test Text File");
            assertThat(uploadedFile.mimeType()).isEqualTo("text/plain");
            assertThat(uploadedFile.sizeBytes()).isEqualTo(Files.size(textFile));
            assertThat(uploadedFile.state()).isIn("ACTIVE", "PROCESSING");
            assertThat(uploadedFile.name()).isNotEmpty();
            assertThat(uploadedFile.createTime()).isNotEmpty();
            assertThat(uploadedFile.updateTime()).isNotEmpty();
            assertThat(uploadedFile.expirationTime()).isNotEmpty();
            assertThat(uploadedFile.sha256Hash()).isNotEmpty();
        }

        @Test
        void should_uploadFileWithoutDisplayName() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("document.txt", "Content without display name");

            // When
            GeminiFile uploadedFile = files.uploadFile(textFile, null);

            // Then
            assertThat(uploadedFile).isNotNull();
            assertThat(uploadedFile.uri()).isNotEmpty();
            assertThat(uploadedFile.displayName()).isEqualTo("document.txt");
            assertThat(uploadedFile.state()).isIn("ACTIVE", "PROCESSING");
        }

        @Test
        void should_handleFileWithSpecialCharactersInName() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path specialFile = createTestFile("test file (2024).txt", "Special characters test");

            // When
            GeminiFile uploadedFile = files.uploadFile(specialFile, "Test File (2024)");

            // Then
            assertThat(uploadedFile).isNotNull();
            assertThat(uploadedFile.uri()).isNotEmpty();
            assertThat(uploadedFile.displayName()).isEqualTo("Test File (2024)");
            assertThat(uploadedFile.state()).isIn("ACTIVE", "PROCESSING");
        }

        @Test
        void should_uploadMultipleFiles() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path file1 = createTestFile("file1.txt", "First file content");
            Path file2 = createTestFile("file2.txt", "Second file content");
            Path file3 = createTestFile("file3.txt", "Third file content");

            // When
            GeminiFile uploadedFile1 = files.uploadFile(file1, "File One");
            GeminiFile uploadedFile2 = files.uploadFile(file2, "File Two");
            GeminiFile uploadedFile3 = files.uploadFile(file3, "File Three");

            // Then
            assertThat(uploadedFile1.uri()).isNotEmpty();
            assertThat(uploadedFile2.uri()).isNotEmpty();
            assertThat(uploadedFile3.uri()).isNotEmpty();
            assertThat(uploadedFile1.uri()).isNotEqualTo(uploadedFile2.uri());
            assertThat(uploadedFile2.uri()).isNotEqualTo(uploadedFile3.uri());
        }

        @Test
        void should_uploadEmptyFile() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path emptyFile = createTestFile("empty.txt", "");

            // When
            GeminiFile uploadedFile = files.uploadFile(emptyFile, "Empty File");

            // Then
            assertThat(uploadedFile).isNotNull();
            assertThat(uploadedFile.uri()).isNotEmpty();
            assertThat(uploadedFile.sizeBytes()).isNull();
            assertThat(uploadedFile.state()).isIn("ACTIVE", "PROCESSING");
        }

        @Test
        void should_uploadFileWithLongDisplayName() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("test.txt", "Content");
            String longDisplayName = "A".repeat(200);

            // When
            GeminiFile uploadedFile = files.uploadFile(textFile, longDisplayName);

            // Then
            assertThat(uploadedFile).isNotNull();
            assertThat(uploadedFile.uri()).isNotEmpty();
            assertThat(uploadedFile.displayName()).isEqualTo(longDisplayName);
        }

        @Test
        void should_verifyExpirationTimeIsSet() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("expiration-test.txt", "Testing expiration");

            // When
            GeminiFile uploadedFile = files.uploadFile(textFile, "Expiration Test");

            // Then
            assertThat(uploadedFile.expirationTime()).isNotEmpty();
            assertThat(uploadedFile.createTime()).isNotEmpty();
            assertThat(uploadedFile.updateTime()).isNotEmpty();
        }

        @Test
        void should_verifySha256HashIsGenerated() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("hash-test.txt", "Testing SHA-256 hash");

            // When
            GeminiFile uploadedFile = files.uploadFile(textFile, "Hash Test");

            // Then
            assertThat(uploadedFile.sha256Hash()).isNotEmpty();
        }
    }

    @Nested
    class GetMetadataTest {

        @Test
        void should_getMetadataForUploadedFile() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("metadata-get-test.txt", "File for metadata retrieval");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Metadata Get Test");

            // When
            GeminiFile retrievedMetadata = files.getMetadata(uploadedFile.name());

            // Then
            assertThat(retrievedMetadata).isNotNull();
            assertThat(retrievedMetadata.name()).isEqualTo(uploadedFile.name());
            assertThat(retrievedMetadata.displayName()).isEqualTo(uploadedFile.displayName());
            assertThat(retrievedMetadata.mimeType()).isEqualTo(uploadedFile.mimeType());
            assertThat(retrievedMetadata.sizeBytes()).isEqualTo(uploadedFile.sizeBytes());
            assertThat(retrievedMetadata.uri()).isEqualTo(uploadedFile.uri());
            assertThat(retrievedMetadata.sha256Hash()).isEqualTo(uploadedFile.sha256Hash());
            assertThat(retrievedMetadata.createTime()).isEqualTo(uploadedFile.createTime());
            assertThat(retrievedMetadata.updateTime()).isEqualTo(uploadedFile.updateTime());
            assertThat(retrievedMetadata.expirationTime()).isEqualTo(uploadedFile.expirationTime());
            assertThat(retrievedMetadata.state()).isIn("ACTIVE", "PROCESSING");
        }

        @Test
        void should_getMetadataMultipleTimes() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("multiple-metadata.txt", "Multiple metadata retrieval test");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Multiple Metadata Test");

            // When
            GeminiFile firstRetrieval = files.getMetadata(uploadedFile.name());
            GeminiFile secondRetrieval = files.getMetadata(uploadedFile.name());
            GeminiFile thirdRetrieval = files.getMetadata(uploadedFile.name());

            // Then
            assertThat(firstRetrieval.name()).isEqualTo(uploadedFile.name());
            assertThat(secondRetrieval.name()).isEqualTo(uploadedFile.name());
            assertThat(thirdRetrieval.name()).isEqualTo(uploadedFile.name());
            assertThat(firstRetrieval.sha256Hash()).isEqualTo(secondRetrieval.sha256Hash());
            assertThat(secondRetrieval.sha256Hash()).isEqualTo(thirdRetrieval.sha256Hash());
        }

        @Test
        void should_getMetadataForEmptyFile() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path emptyFile = createTestFile("empty-metadata.txt", "");
            GeminiFile uploadedFile = files.uploadFile(emptyFile, "Empty Metadata File");

            // When
            GeminiFile metadata = files.getMetadata(uploadedFile.name());

            // Then
            assertThat(metadata).isNotNull();
            assertThat(metadata.name()).isEqualTo(uploadedFile.name());
            assertThat(metadata.sizeBytes()).isNull();
        }

        @Test
        void should_throwExceptionWhenGettingMetadataForNonExistentFile() {
            // Given
            GeminiFiles files = createGeminiFiles();
            String nonExistentName = "files/non-existent-file-99999";

            // When/Then
            assertThatThrownBy(() -> files.getMetadata(nonExistentName))
                    .isInstanceOf(GeminiFiles.GeminiUploadFailureException.class)
                    .hasMessageContaining("Failed to retrieve metadata for file")
                    .hasMessageContaining(nonExistentName);
        }

        @Test
        void should_getMetadataImmediatelyAfterUpload() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("immediate-metadata.txt", "Immediate metadata test");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Immediate Metadata");

            // When
            GeminiFile metadata = files.getMetadata(uploadedFile.name());

            // Then
            assertThat(metadata).isNotNull();
            assertThat(metadata.name()).isEqualTo(uploadedFile.name());
            assertThat(metadata.displayName()).isEqualTo(uploadedFile.displayName());
        }

        @Test
        void should_verifyMetadataMatchesListedFile() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("list-match-test.txt", "List match test");
            GeminiFile uploadedFile = files.uploadFile(textFile, "List Match Test");

            // When
            GeminiFile directMetadata = files.getMetadata(uploadedFile.name());
            List<GeminiFile> listedFiles = files.listFiles();
            GeminiFile listedMetadata = listedFiles.stream()
                    .filter(f -> f.name().equals(uploadedFile.name()))
                    .findFirst()
                    .orElseThrow();

            // Then
            assertThat(directMetadata.name()).isEqualTo(listedMetadata.name());
            assertThat(directMetadata.displayName()).isEqualTo(listedMetadata.displayName());
            assertThat(directMetadata.mimeType()).isEqualTo(listedMetadata.mimeType());
            assertThat(directMetadata.sizeBytes()).isEqualTo(listedMetadata.sizeBytes());
            assertThat(directMetadata.uri()).isEqualTo(listedMetadata.uri());
            assertThat(directMetadata.sha256Hash()).isEqualTo(listedMetadata.sha256Hash());
        }

        @Test
        void should_throwExceptionWhenGettingMetadataForDeletedFile() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("deleted-metadata.txt", "File to delete");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Deleted Metadata Test");

            // Delete the file
            files.deleteFile(uploadedFile.name());

            // When/Then
            assertThatThrownBy(() -> files.getMetadata(uploadedFile.name()))
                    .isInstanceOf(GeminiFiles.GeminiUploadFailureException.class)
                    .hasMessageContaining("Failed to retrieve metadata for file");
        }
    }

    @Nested
    class ListFilesTest {

        @Test
        void should_listUploadedFiles() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("list-test.txt", "File for listing test");
            GeminiFile uploadedFile = files.uploadFile(textFile, "List Test File");

            // When
            List<GeminiFile> listedFiles = files.listFiles();

            // Then
            assertThat(listedFiles).isNotNull();
            assertThat(listedFiles.isEmpty()).isFalse();
            assertThat(listedFiles.stream().anyMatch(f -> f.name().equals(uploadedFile.name())))
                    .isTrue();
        }

        @Test
        void should_listFilesWithCorrectMetadata() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("metadata-test.txt", "Metadata test content");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Metadata Test");

            // When
            List<GeminiFile> listedFiles = files.listFiles();

            // Then
            GeminiFile foundFile = listedFiles.stream()
                    .filter(f -> f.name().equals(uploadedFile.name()))
                    .findFirst()
                    .orElseThrow();

            assertThat(foundFile.name()).isEqualTo(uploadedFile.name());
            assertThat(foundFile.displayName()).isEqualTo(uploadedFile.displayName());
            assertThat(foundFile.mimeType()).isEqualTo(uploadedFile.mimeType());
            assertThat(foundFile.sizeBytes()).isEqualTo(uploadedFile.sizeBytes());
            assertThat(foundFile.uri()).isEqualTo(uploadedFile.uri());
            assertThat(foundFile.state()).isIn("ACTIVE", "PROCESSING");
        }

        @Test
        void should_listFilesAfterDeletion() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("delete-list-test.txt", "File to delete");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Delete List Test");

            // When
            files.deleteFile(uploadedFile.name());
            List<GeminiFile> listedFiles = files.listFiles();

            // Then
            assertThat(listedFiles.stream().noneMatch(f -> f.name().equals(uploadedFile.name())))
                    .isTrue();
        }
    }

    @Nested
    class DeleteFilesTest {

        @Test
        void should_deleteUploadedFile() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("delete-test.txt", "File to delete");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Delete Test File");

            // When
            files.deleteFile(uploadedFile.name());

            // Then
            List<GeminiFile> listedFiles = files.listFiles();
            assertThat(listedFiles.stream().noneMatch(f -> f.name().equals(uploadedFile.name())))
                    .isTrue();
        }

        @Test
        void should_deleteMultipleFiles() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path file1 = createTestFile("delete1.txt", "First file to delete");
            Path file2 = createTestFile("delete2.txt", "Second file to delete");

            GeminiFile uploaded1 = files.uploadFile(file1, "Delete File 1");
            GeminiFile uploaded2 = files.uploadFile(file2, "Delete File 2");

            // When
            files.deleteFile(uploaded1.name());
            files.deleteFile(uploaded2.name());

            // Then
            List<GeminiFile> listedFiles = files.listFiles();
            assertThat(listedFiles.stream().noneMatch(f -> f.name().equals(uploaded1.name())))
                    .isTrue();
            assertThat(listedFiles.stream().noneMatch(f -> f.name().equals(uploaded2.name())))
                    .isTrue();
        }

        @Test
        void should_deleteFileImmediatelyAfterUpload() throws Exception {
            // Given
            GeminiFiles files = createGeminiFiles();
            Path textFile = createTestFile("immediate-delete.txt", "Delete immediately");
            GeminiFile uploadedFile = files.uploadFile(textFile, "Immediate Delete");

            // When
            files.deleteFile(uploadedFile.name());

            // Then
            List<GeminiFile> listedFiles = files.listFiles();
            assertThat(listedFiles.stream().noneMatch(f -> f.name().equals(uploadedFile.name())))
                    .isTrue();
        }
    }

    private GeminiFiles createGeminiFiles() {
        return new GeminiFiles(GOOGLE_AI_GEMINI_API_KEY, null, null);
    }

    private Path createTestFile(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
