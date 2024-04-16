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