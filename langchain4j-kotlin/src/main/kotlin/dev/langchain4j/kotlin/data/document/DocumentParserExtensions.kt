package dev.langchain4j.kotlin.data.document

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.DocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

/**
 * Asynchronously parses a document from the specified document source
 * using the given coroutine context.
 *
 * @param source The [dev.langchain4j.data.document.DocumentSource] from which the document will be parsed.
 * @param context The [CoroutineContext] to use for asynchronous execution,
 * defaults to [Dispatchers.IO].
 * @return The parsed [dev.langchain4j.data.document.Document],
 * potentially with merged metadata from the document source.
 */
public suspend fun DocumentParser.parseAsync(
    source: DocumentSource,
    context: CoroutineContext = Dispatchers.IO
): Document {
    val document =
        source.inputStream().use { inputStream ->
            return@use parseAsync(inputStream, context)
        }
    val documentSourceMetadata = source.metadata()
    return if (documentSourceMetadata.toMap().isNotEmpty()) {
        Document.from(document.text(), documentSourceMetadata.merge(document.metadata()))
    } else {
        document
    }
}

/**
 * Asynchronously parses a document from the provided input stream using the specified dispatcher.
 *
 * @param input The [InputStream] from which the document will be parsed.
 * @param context The CoroutineContext to use for asynchronous execution,
 *                  defaults to `[Dispatchers.IO]`.
 * @return The parsed Document.
 */
public suspend fun DocumentParser.parseAsync(
    input: InputStream,
    context: CoroutineContext = Dispatchers.IO
): Document =
    withContext(context) {
        parse(input)
    }
