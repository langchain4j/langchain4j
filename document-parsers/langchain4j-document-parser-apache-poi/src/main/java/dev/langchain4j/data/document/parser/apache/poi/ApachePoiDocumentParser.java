package dev.langchain4j.data.document.parser.apache.poi;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.poi.EmptyFileException;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

import java.io.IOException;
import java.io.InputStream;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Parses Microsoft Office file into a {@link Document} using Apache POI library.
 * This parser supports various file formats, including doc, docx, ppt, pptx, xls, and xlsx.
 * For detailed information on supported formats,
 * please refer to the <a href="https://poi.apache.org/">official Apache POI website</a>.
 * <p>
 * Apache POI rejects highly compressed ZIP-based documents (DOCX, PPTX, XLSX) with a check of their compression
 * ratio. As soon as Apache Tika parses a document in the same JVM, or creates its parsers there (for example with
 * {@code new AutoDetectParser()}, also when that parser is then passed to Tika's {@code ForkParser}), Tika turns this
 * check off for the whole JVM. It also raises POI's limit on the number of files inside such a document from 1,000 to
 * 10,000 for the whole JVM. This also affects this parser, for example when
 * {@code langchain4j-document-parser-apache-tika} or Easy RAG is used in the same application.
 * <p>
 * Without this check, some small, highly compressed documents can need a lot of memory.
 * An {@link OutOfMemoryError} in the JVM of your application can affect the whole application, even with a heap limit
 * ({@code -Xmx}). So if you parse documents from sources you do not trust, parse them in a separate process with its
 * own heap limit.
 */
public class ApachePoiDocumentParser implements DocumentParser {

    @Override
    public Document parse(InputStream inputStream) {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(inputStream)) {
            String text = extractor.getText();

            if (isNullOrBlank(text)) {
                throw new BlankDocumentException();
            }

            return Document.from(text);
        } catch (EmptyFileException e) {
            throw new BlankDocumentException();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}