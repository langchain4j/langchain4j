package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

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
            PDDocumentInformation documentInformation = pdfDocument.getDocumentInformation();
            Metadata metadata =  toMetadata(documentInformation);
            return Document.from(text, metadata);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Metadata toMetadata(PDDocumentInformation documentInformation) {
        Metadata metadata = new Metadata();
        Set<String> metadataKeys = documentInformation.getMetadataKeys();
        for (String metadataKey : metadataKeys) {
            String value = documentInformation.getCustomMetadataValue(metadataKey);
            if (value != null) metadata.put(metadataKey, value);
        }
        return metadata;
    }
}
