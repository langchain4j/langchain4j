package dev.langchain4j.data.document.loader.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * A failure while loading one file ends the directory walk early. The stream returned by
 * {@link Files#walk} keeps the directories it is walking open until it is closed, so the walk has
 * to release them even when it does not run to completion. A {@code null} connection makes loading
 * the first file fail, which exercises exactly that path without a database.
 */
class OracleDocumentLoaderDirectoryWalkTest {

    /** Linux lists the file descriptors a process holds as symbolic links to what they point at. */
    private static final Path OPEN_FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    private final OracleDocumentLoader loader = new OracleDocumentLoader(null);

    @TempDir
    Path root;

    @Test
    void should_propagate_a_failure_while_loading_a_file_from_a_directory() throws IOException {
        createDocumentInNestedDirectory();

        assertThatThrownBy(() -> loader.loadDocuments(directoryPreference())).isInstanceOf(RuntimeException.class);
    }

    @Test
    @EnabledOnOs(OS.LINUX) // relies on OPEN_FILE_DESCRIPTORS
    void should_close_the_directories_being_walked_when_loading_a_file_fails() throws IOException {
        createDocumentInNestedDirectory();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> loader.loadDocuments(directoryPreference()))
                    .isInstanceOf(RuntimeException.class);
        }

        assertThat(openFileDescriptorsPointingInto(root.toRealPath())).isZero();
    }

    /**
     * The walk keeps every directory on the way down open, so the document sits two levels deep:
     * a leak shows up as one descriptor per level, not just one for the root.
     */
    private void createDocumentInNestedDirectory() throws IOException {
        Path nestedDirectory = Files.createDirectories(root.resolve("reports").resolve("archive"));
        Files.writeString(nestedDirectory.resolve("document.txt"), "some content");
    }

    private String directoryPreference() {
        // the preference is JSON, so a Windows path separator has to be escaped
        return "{\"dir\": \"" + root.toString().replace("\\", "\\\\") + "\"}";
    }

    private static long openFileDescriptorsPointingInto(Path directory) throws IOException {
        try (Stream<Path> descriptors = Files.list(OPEN_FILE_DESCRIPTORS)) {
            return descriptors
                    .filter(descriptor -> {
                        try {
                            return Files.readSymbolicLink(descriptor).startsWith(directory);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .count();
        }
    }
}
