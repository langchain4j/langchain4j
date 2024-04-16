package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.pdfbox.pdmodel.PDDocument;
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
        try {
            PDDocument pdfDocument = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdfDocument);
            pdfDocument.close();

            if (isNullOrBlank(text)) {
                throw new BlankDocumentException();
            }

            return Document.from(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
