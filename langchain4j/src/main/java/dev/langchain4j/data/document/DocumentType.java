package dev.langchain4j.data.document;

import static java.util.Arrays.asList;

public enum DocumentType {

    TXT(".txt"),
    PDF(".pdf"),
    HTML(".html", ".htm", ".xhtml"),
    DOC(".doc", ".docx"),
    XLS(".xls", ".xlsx"),
    PPT(".ppt", ".pptx"),
    UNKNOWN;

    private final Iterable<String> supportedExtensions;

    DocumentType(String... supportedExtensions) {
        this.supportedExtensions = asList(supportedExtensions);
    }

    public static DocumentType of(String fileName) {

        for (DocumentType documentType : values()) {
            for (String supportedExtension : documentType.supportedExtensions) {
                if (fileName.toLowerCase().endsWith(supportedExtension)) {
                    return documentType;
                }
            }
        }

        return UNKNOWN;
    }
}
