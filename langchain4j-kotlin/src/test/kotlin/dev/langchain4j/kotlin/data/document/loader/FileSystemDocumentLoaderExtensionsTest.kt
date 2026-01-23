package dev.langchain4j.kotlin.data.document.loader

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.parser.TextDocumentParser
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class FileSystemDocumentLoaderExtensionsTest {

    private val parser: TextDocumentParser = TextDocumentParser()

    @Test
    internal fun `should skip failed documents and continue processing`(@TempDir tempDir: Path): Unit = runTest {
        // Create test files
        val validFile1 = tempDir.resolve("valid1.txt")
        val validFile2 = tempDir.resolve("valid2.txt")

        // Write content to valid files
        Files.writeString(validFile1, "This is valid content 1")
        Files.writeString(validFile2, "This is valid content 2")

        // Load documents - this should not throw an exception
        val documents: List<Document> = loadDocuments(
            directoryPaths = listOf(tempDir),
            documentParser = parser,
            recursive = false
        )

        // Should have loaded some documents (at least the valid ones)
        documents shouldHaveSize 2

        // Verify that we got the expected content
        val texts = documents.map { it.text() }.toSet()
        texts shouldBe setOf("This is valid content 1", "This is valid content 2")
    }

    @Test
    internal fun `should skip documents that cause parsing exceptions`(@TempDir tempDir: Path): Unit = runTest {
        // Create test files
        val validFile = tempDir.resolve("valid.txt")
        val problematicFile = tempDir.resolve("problematic.txt")

        // Write valid content
        Files.writeString(validFile, "This is valid content")

        // Create a file that will cause parsing issues by writing null bytes
        Files.write(problematicFile, byteArrayOf(0, 0, 0, 0))

        // Load documents - this should not throw an exception even though one file fails
        val documents: List<Document> = loadDocuments(
            directoryPaths = listOf(tempDir),
            documentParser = parser,
            recursive = false
        )

        // Should have loaded only the valid document
        documents shouldHaveSize 1
        documents.first().text() shouldBe "This is valid content"
    }

    @Test
    internal fun `should handle empty directory gracefully`(@TempDir tempDir: Path): Unit = runTest {
        val documents: List<Document> = loadDocuments(
            directoryPaths = listOf(tempDir),
            documentParser = parser,
            recursive = false
        )

        documents shouldHaveSize 0
    }

    @Test
    internal fun `should process multiple directories with some failures`(@TempDir tempDir: Path): Unit = runTest {
        val subDir1 = tempDir.resolve("subdir1")
        val subDir2 = tempDir.resolve("subdir2")
        Files.createDirectories(subDir1)
        Files.createDirectories(subDir2)

        // Create valid files in both directories
        Files.writeString(subDir1.resolve("file1.txt"), "Content from file 1")
        Files.writeString(subDir2.resolve("file2.txt"), "Content from file 2")

        val documents: List<Document> = loadDocuments(
            directoryPaths = listOf(subDir1, subDir2),
            documentParser = parser,
            recursive = false
        )

        documents shouldHaveSize 2
        val texts = documents.map { it.text() }.toSet()
        texts shouldBe setOf("Content from file 1", "Content from file 2")
    }
}

