package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Parses PDF file into a {@link Document} using Apache PDFBox library
 */
public class ApachePdfBoxDocumentParser implements DocumentParser {

    @Override
    public Document parse(InputStream inputStream) {
        try (PDDocument pdfDocument = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdfDocument);
            if (isNullOrBlank(text)) {
                throw new BlankDocumentException();
            }
            Metadata metadata = toMetadata(pdfDocument.getDocumentInformation());
            return Document.from(text, metadata);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Metadata toMetadata(PDDocumentInformation documentInformation) {
        Metadata metadata = new Metadata();
        for (String metadataKey : documentInformation.getMetadataKeys()) {
            String value = documentInformation.getCustomMetadataValue(metadataKey);
            if (value != null) metadata.put(metadataKey, value);
        }
        return metadata;
    }
}
