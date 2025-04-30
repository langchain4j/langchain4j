package dev.langchain4j.kotlin.data.document.loader

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.kotlin.data.document.parseAsync
import dev.langchain4j.data.document.source.FileSystemSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

private val logger = LoggerFactory.getLogger(FileSystemDocumentLoader::class.java)

/**
 * Asynchronously loads documents from the specified directories.
 *
 * @param directoryPaths A list of directories from which documents should be loaded.
 * @param documentParser The parser to convert files into [Document] objects.
 * @param recursive Determines whether subdirectories should also be searched for documents. Defaults to `false`.
 * @param pathMatcher An optional filter to match file paths against specific patterns.
 * @param context The CoroutineContext to be used for asynchronous operations. Defaults to [Dispatchers.IO].
 * @return A list of Document objects representing the loaded documents.
 */
public suspend fun loadDocuments(
    directoryPaths: List<Path>,
    documentParser: DocumentParser,
    recursive: Boolean = false,
    pathMatcher: PathMatcher? = null,
    context: CoroutineContext = Dispatchers.IO
): List<Document> =
    coroutineScope {
        // Validate all paths before processing
        directoryPaths.forEach { path ->
            require(path.exists()) { "Path doesn't exist: $path" }
            require(path.isDirectory()) { "Path is not a directory: $path" }
        }
        // Collect all files from the directory paths matching the pathMatcher
        val matchedFiles =
            directoryPaths.flatMap { path ->
                val files = mutableListOf<Path>()
                // Matches all if no pathMatcher is provided
                val matcher: PathMatcher = pathMatcher ?: PathMatcher { true }

                // Traverse directories conditionally based on the recursive flag
                val fileStream = if (recursive) Files.walk(path) else Files.walk(path, 1)

                fileStream.use { stream ->
                    stream
                        .filter { file ->
                            Files.isRegularFile(file) && matcher.matches(file)
                        }.forEach { file ->
                            files.add(file)
                        }
                }
                files
            }

        // Process each file in parallel
        matchedFiles
            .map { file ->
                async(context) {
                    documentParser.parseAsync(
                        FileSystemSource(file),
                        context
                    )
                }
            }.awaitAll()
            .map { document ->
                val metadata = document.metadata()
                logger.info(
                    "Loaded document: {}/{}",
                    metadata.getString(Document.ABSOLUTE_DIRECTORY_PATH),
                    metadata.getString(Document.FILE_NAME)
                )
                document
            }
    }
