package dev.langchain4j.data.document.source;

import static dev.langchain4j.data.document.Document.ABSOLUTE_DIRECTORY_PATH;
import static dev.langchain4j.data.document.Document.FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link FileSystemSource}.
 * <p>
 * This test class verifies the following behaviors:
 * </p>
 * <ul>
 * <li>Creation of {@code FileSystemSource} from various inputs: {@code Path},
 * {@code String}, {@code File}, and {@code URI}</li>
 * <li>Correct extraction of metadata fields like {@code FILE_NAME} and
 * {@code ABSOLUTE_DIRECTORY_PATH}</li>
 * <li>Reading file content via {@code inputStream()}</li>
 * <li>Proper error handling when given invalid paths (e.g., non-existent or
 * directory)</li>
 * <li>Handling edge cases like {@code path.getParent()} returning
 * {@code null}</li>
 * <li>Temporary file creation and cleanup for isolated test scenarios</li>
 * </ul>
 *
 * <p>
 * Mocks are used where necessary (e.g., to simulate
 * {@code getParent() == null}) to ensure edge cases are covered.
 * </p>
 *
 * @see FileSystemSource
 * @see dev.langchain4j.data.document.Document
 * @see dev.langchain4j.data.document.Metadata
 */
public class FileSystemSourceTests {

    private final Path testFilePath = Paths.get("src/test/resources/fileSystemSourceTests/sample.txt");

    @Test
    void should_create_file_system_source_from_various_inputs_and_resolve_metadata_correctly() {
        FileSystemSource fromPath = FileSystemSource.from(testFilePath);
        FileSystemSource fromString = FileSystemSource.from(testFilePath.toString());
        FileSystemSource fromFile = FileSystemSource.from(testFilePath.toFile());
        FileSystemSource fromUri = FileSystemSource.from(testFilePath.toUri());

        for (FileSystemSource source : new FileSystemSource[] {fromPath, fromString, fromFile, fromUri}) {
            Metadata metadata = source.metadata();
            assertEquals("sample.txt", metadata.getString(FILE_NAME));
            assertTrue(metadata.getString(ABSOLUTE_DIRECTORY_PATH).endsWith("fileSystemSourceTests"));
            assertTrue(source.toString().contains("sample.txt"));
        }
    }

    @Test
    void should_open_input_stream_and_read_file_contents() throws IOException {
        FileSystemSource source = FileSystemSource.from(testFilePath);

        try (InputStream in = source.inputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line = reader.readLine();
            assertEquals("This is a test file.", line);
        }
    }

    @Test
    void should_throw_exception_for_null_or_invalid_or_directory_path() {
        assertThrows(IllegalArgumentException.class, () -> new FileSystemSource(null));

        Path nonExistentPath = Paths.get("src/test/resources/fileSystemSourceTests/does-not-exist.txt");
        Path directoryPath = Paths.get("src/test/resources/fileSystemSourceTests");

        for (Path path : new Path[] {nonExistentPath, directoryPath}) {
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> FileSystemSource.from(path));
            assertTrue(ex.getMessage().contains("Invalid file path"));
        }
    }

    @Test
    void should_create_source_from_temp_file_and_clean_up_after() throws IOException {
        // Step 1: Create a temp file in the project root directory (".")
        Path tempFile = Files.createTempFile(Paths.get("."), "fs-test-", ".txt");

        // Step 2: Write some content
        String expectedContent = "Temporary test content.";
        Files.writeString(tempFile, expectedContent, StandardCharsets.UTF_8);

        try {
            // Step 3: Create FileSystemSource
            FileSystemSource source = FileSystemSource.from(tempFile);

            // Step 4: Assert metadata
            Metadata metadata = source.metadata();
            assertEquals(tempFile.getFileName().toString(), metadata.getString(FILE_NAME));
            assertEquals(tempFile.toAbsolutePath().getParent().toString(), metadata.getString(ABSOLUTE_DIRECTORY_PATH));

            // Step 5: Assert content
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(source.inputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                assertEquals(expectedContent, line);
            }

        } finally {
            // Step 6: Delete the file after test
            Files.deleteIfExists(tempFile);
            assertFalse(Files.exists(tempFile), "Temp file should have been deleted");
        }
    }

    @Test
    void should_set_empty_absolute_directory_path_when_parent_is_null() {
        // Mock a Path that:
        // - exists (simulate Files.exists check)
        // - is not a directory
        // - has no parent (toAbsolutePath().getParent() == null)

        Path mockPath = mock(Path.class);
        when(mockPath.getFileName()).thenReturn(Path.of("orphan.txt"));
        when(mockPath.toAbsolutePath()).thenReturn(mockPath);
        when(mockPath.toAbsolutePath().getParent()).thenReturn(null);

        // Mock static Files.exists() and Files.isDirectory() using Mockito-inline
        try (var mockedFiles = Mockito.mockStatic(java.nio.file.Files.class)) {
            mockedFiles.when(() -> Files.exists(mockPath)).thenReturn(true);
            mockedFiles.when(() -> Files.isDirectory(mockPath)).thenReturn(false);

            // Create source
            FileSystemSource source = new FileSystemSource(mockPath);

            Metadata metadata = source.metadata();
            assertEquals("orphan.txt", metadata.getString(FILE_NAME));
            assertEquals("", metadata.getString(ABSOLUTE_DIRECTORY_PATH));
        }
    }
}
