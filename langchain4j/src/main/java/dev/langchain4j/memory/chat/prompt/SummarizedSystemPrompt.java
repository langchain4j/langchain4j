package dev.langchain4j.memory.chat.prompt;

import dev.langchain4j.model.input.structured.StructuredPrompt;

/**
 * This prompt template is leveraged by the {@link dev.langchain4j.memory.chat.SummarizedTokenWindowChatMemory} to encapsulate
 * both the original SystemMessage and the summary of messages that have slid out of the token window.
 */

@StructuredPrompt({
        "{",
        "  \"originalInstructions\": \"{{originalSystemMessage}}\",",
        "  \"conversationSummary\": \"{{summary}}\"",
        "}"
})
public class SummarizedSystemPrompt {

    private final String originalSystemMessage;
    private final String summary;

    public SummarizedSystemPrompt(String originalSystemMessageText, String summary) {
        this.originalSystemMessage = originalSystemMessageText;
        this.summary = summary;
    }
}
