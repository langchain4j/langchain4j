package dev.langchain4j.kotlin.data.document.loader

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import dev.langchain4j.data.document.DocumentSource
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.source.FileSystemSource
import dev.langchain4j.kotlin.data.document.loadAsync
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
}
