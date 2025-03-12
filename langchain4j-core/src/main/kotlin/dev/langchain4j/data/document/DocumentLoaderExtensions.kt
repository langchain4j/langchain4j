package dev.langchain4j.data.document

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Asynchronously loads a document from the specified source using a given parser.
 *
 * @param source The [DocumentSource] from which the document will be loaded.
 * @param parser The [DocumentParser] to parse the loaded document.
 * @param context The [CoroutineContext] to use for asynchronous execution,
 *                  defaults to `Dispatchers.IO`.
 * @return The loaded and parsed Document.
 */
public suspend fun loadAsync(
    source: DocumentSource,
    parser: DocumentParser,
    context: CoroutineContext = Dispatchers.IO
): Document =
    withContext(context) {
        DocumentLoader.load(source, parser)
    }
