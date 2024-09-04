package dev.langchain4j.data.message;

/**
 * The type of content, e.g. text or image.
 * Maps to implementations of {@link Content}.
 */
public enum ContentType {
    /**
     * Text content.
     */
    TEXT(TextContent.class),
    /**
     * Text file content.
     */
    TEXT_FILE(TextFileContent.class),
    /**
     * Image content.
     */
    IMAGE(ImageContent.class),
    /**
     * Audio content.
     */
    AUDIO(AudioContent.class),
    /**
     * Video content.
     */
    VIDEO(VideoContent.class),
    /**
     * PDF file content.
     */
    PDF(PdfFileContent.class);

    private final Class<? extends Content> contentClass;

    ContentType(Class<? extends Content> contentClass) {
        this.contentClass = contentClass;
    }

    /**
     * Returns the class of the content type.
     * @return The class of the content type.
     */
    public Class<? extends Content> getContentClass() {
        return contentClass;
    }
}
