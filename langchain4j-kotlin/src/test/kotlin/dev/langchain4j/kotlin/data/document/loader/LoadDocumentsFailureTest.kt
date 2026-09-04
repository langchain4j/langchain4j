package dev.langchain4j.kotlin.data.document.loader

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.parser.TextDocumentParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.file.Path

// See issue #4347: a single unparsable file (e.g. blank) used to abort the entire batch with an
// unhandled exception, losing every already-loaded document in the same call.
internal class LoadDocumentsFailureTest {
    private val parser = TextDocumentParser()

    @Test
    fun `Should skip files that fail to parse and still return the ones that succeeded`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = false,
                    documentParser = parser,
                    directoryPaths = listOf(Path.of("./src/test/resources/loadDocumentsFailureTest/mixedFiles"))
                )

            documents shouldHaveSize 2
            val names = documents.map { it.metadata().getString("file_name") }
            names shouldContainExactlyInAnyOrder listOf("good.txt", "good2.txt")
        }

    @Test
    fun `Should return an empty list, not throw, when every file fails to parse`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = false,
                    documentParser = parser,
                    directoryPaths = listOf(Path.of("./src/test/resources/loadDocumentsFailureTest/allBad"))
                )

            documents.shouldBeEmpty()
        }

    @Test
    fun `Should skip failing files at any depth when loading recursively`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = true,
                    documentParser = parser,
                    directoryPaths = listOf(Path.of("./src/test/resources/loadDocumentsFailureTest/recursiveMixed"))
                )

            documents shouldHaveSize 2
            val names = documents.map { it.metadata().getString("file_name") }
            names shouldContainExactlyInAnyOrder listOf("top.txt", "nested_good.txt")
        }

    @Test
    fun `Should still return all documents when none of them fail to parse`() =
        runTest {
            val documents =
                loadDocuments(
                    recursive = true,
                    documentParser = parser,
                    directoryPaths = listOf(Path.of("./src/test/resources/asyncDocumentLoaderTest"))
                )

            documents shouldHaveSize 4
        }

    @Test
    fun `Should propagate CancellationException instead of treating it as a parse failure`() =
        runTest {
            val cancellingParser =
                object : DocumentParser {
                    override fun parse(inputStream: InputStream): Document {
                        throw CancellationException("simulated external cancellation")
                    }
                }

            shouldThrow<CancellationException> {
                loadDocuments(
                    recursive = false,
                    documentParser = cancellingParser,
                    directoryPaths = listOf(Path.of("./src/test/resources/loadDocumentsFailureTest/mixedFiles"))
                )
            }
        }
}
