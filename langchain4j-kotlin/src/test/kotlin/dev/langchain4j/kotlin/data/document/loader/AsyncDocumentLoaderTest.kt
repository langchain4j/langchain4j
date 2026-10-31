package dev.langchain4j.kotlin.data.document.loader

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import dev.langchain4j.data.document.DocumentSource
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.source.FileSystemSource
import dev.langchain4j.kotlin.data.document.loadAsync
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

internal class AsyncDocumentLoaderTest {
    private lateinit var documentSource: DocumentSource
    private val parser = TextDocumentParser()

    @BeforeEach
    fun beforeEach() {
        documentSource =
            FileSystemSource(
                Paths.get("./src/test/resources/miles-of-smiles-terms-of-use.txt")
            )
    }

    @Test
    fun `Should load documents asynchronously`() =
        runTest {
            val document = loadAsync(documentSource, parser)
            document.text() shouldContain "Miles of Smiles Car Rental Services"
            document.metadata() shouldNotBeNull {
                getString("file_name") shouldContain "miles-of-smiles-terms-of-use.txt"
            }
        }

    @Test
    fun `Should parse documents asynchronously`() =
        runTest {
            val document = parser.parse(documentSource.inputStream())
            assertThat(document.text()).contains("Miles of Smiles Car Rental Services")
            assertThat(document.metadata()).isNotNull()
        }

    @Test
    fun `Should loadDocuments`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = true,
                    documentParser = parser,
                    directoryPaths = listOf(Path.of("./src/test/resources/asyncDocumentLoaderTest"))
                )
            documents shouldHaveSize 4

            documents.forEach {
                assertThat(it.text()).isNotEmpty()
                it.metadata().shouldNotBeNull {}
            }

            val documentNames = documents.map { it.metadata().getString("file_name") }
            documentNames shouldContainExactlyInAnyOrder
                    listOf(
                        "file1.txt",
                        "file2.txt",
                        "test-file-3.banana",
                        "test-file-4.banana"
                    )
        }

    @Test
    fun `Should match a non-recursive glob against paths relative to the traversed directory`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = false,
                    documentParser = parser,
                    directoryPaths = listOf(testDirectory),
                    pathMatcher = globMatcher("glob:*.txt")
                )

            documents.map { it.metadata().getString("file_name") } shouldContainExactly listOf("file1.txt")
        }

    @Test
    fun `Should return no documents when the glob matches nothing`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = false,
                    documentParser = parser,
                    directoryPaths = listOf(testDirectory),
                    pathMatcher = globMatcher("glob:*.pdf")
                )

            documents.shouldBeEmpty()
        }

    @Test
    fun `Should keep matching a recursive double-star glob without a separator`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = true,
                    documentParser = parser,
                    directoryPaths = listOf(testDirectory),
                    pathMatcher = globMatcher("glob:**.banana")
                )

            documents.map { it.metadata().getString("file_name") } shouldContainExactlyInAnyOrder
                    listOf(
                        "test-file-3.banana",
                        "test-file-4.banana"
                    )
        }

    @Test
    fun `Should not match top-level files with a recursive glob requiring a separator`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = true,
                    documentParser = parser,
                    directoryPaths = listOf(testDirectory),
                    pathMatcher = globMatcher("glob:**/*.txt")
                )

            // "file1.txt" sits at the root, so its relative path has no separator and does not match
            documents.map { it.metadata().getString("file_name") } shouldContainExactly listOf("file2.txt")
        }

    private fun globMatcher(pattern: String) = FileSystems.getDefault().getPathMatcher(pattern)

    private companion object {
        private val testDirectory = Path.of("./src/test/resources/asyncDocumentLoaderTest")
    }
}
