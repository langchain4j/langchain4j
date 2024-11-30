package dev.langchain4j.micrometer.conventions;

// Copied from AiObservationEventNames.java in spring-ai-core
public enum AiObservationEventNames {
    /**
     * Prompt for content generation.
     */
    CONTENT_PROMPT("gen_ai.content.prompt"),

    /**
     * Completion of content generation.
     */
    CONTENT_COMPLETION("gen_ai.content.completion");

    private final String value;

    AiObservationEventNames(String value) {
        this.value = value;
    }

    /**
     * Return the value of the event name.
     * @return the value of the event name
     */
    public String value() {
        return this.value;
    }
}
