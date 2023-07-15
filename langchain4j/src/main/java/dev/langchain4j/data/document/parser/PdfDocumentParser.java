package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

public class PdfDocumentParser implements DocumentParser {

    @Override
    public Document parse(InputStream inputStream) {
        try {
            PDDocument pdfDocument = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(pdfDocument);
            pdfDocument.close();
            return Document.from(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
