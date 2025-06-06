package dev.langchain4j.kotlin.data.document

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.Document.ABSOLUTE_DIRECTORY_PATH
import dev.langchain4j.data.document.Document.FILE_NAME
import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.DocumentSource
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.kotlin.data.document.parseAsync
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
internal class DocumentParserExtensionsKtTest {
    @Mock
    private lateinit var documentParser: DocumentParser

    @Mock
    private lateinit var documentSource: DocumentSource

    @Test
    fun `parseAsync should parse document and return it combined metadata`() {
        val documentContent = "parsed document content"
        runTest {
            val inputStream = documentContent.byteInputStream()
            val documentMetadata = Metadata.from(mapOf<String, Any?>("title" to "Bar"))
            val fileMetadata =
                Metadata.from(
                    mapOf(
                        ABSOLUTE_DIRECTORY_PATH to "foo",
                        FILE_NAME to "bar.txt"
                    )
                )
            val document = Document.from(documentContent, documentMetadata)

            whenever(documentSource.inputStream()).thenReturn(inputStream)
            whenever(documentParser.parse(inputStream)).thenReturn(document)
            whenever(documentSource.metadata()).thenReturn(fileMetadata)

            val result = documentParser.parseAsync(documentSource, Dispatchers.IO)

            assertThat(result.text()).isEqualTo(documentContent)
            assertThat(result.metadata().toMap())
                .containsOnly(
                    ABSOLUTE_DIRECTORY_PATH to "foo",
                    FILE_NAME to "bar.txt",
                    "title" to "Bar"
                )
        }
    }

    @Test
    fun `parseAsync should return parser document when no source metadata is present`() {
        val documentContent = "parsed document content"
        runTest {
            val inputStream = documentContent.byteInputStream()
            val documentMetadata = Metadata.from(mapOf<String, Any?>("title" to "Bar"))
            val fileMetadata = Metadata.from(mapOf<String, Any?>())
            val document = Document.from(documentContent, documentMetadata)

            whenever(documentSource.inputStream()).thenReturn(inputStream)
            whenever(documentParser.parse(inputStream)).thenReturn(document)
            whenever(documentSource.metadata()).thenReturn(fileMetadata)

            val result = documentParser.parseAsync(documentSource, Dispatchers.IO)

            result shouldBeSameInstanceAs document
        }
    }
}
