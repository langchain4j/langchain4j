package dev.langchain4j.memory.chat.prompt;

import dev.langchain4j.model.input.structured.StructuredPrompt;

/**
 * This prompt is leveraged by the {@link dev.langchain4j.memory.chat.SummarizedTokenWindowChatMemory} to build a summary
 * of messages that have slid out of the token window.
 */

@StructuredPrompt({
        "You are tasked with progressively summarize the lines of conversation provided, integrating new messages into a previous summary returning a new summary.",
        "",
        "EXAMPLE",
        "Current summary:",
        "The user asks the AI for advice on starting a business. The AI suggests researching the market.",
        "",
        "New lines of conversation:",
        "User: How should I go about researching the market?",
        "AI: Start by identifying your target audience and studying their needs and preferences.",
        "",
        "New summary:",
        "The user asks the AI for advice on starting a business. The AI suggests researching the market by identifying the target audience and studying their needs and preferences.",
        "END OF EXAMPLE",
        "",
        "Current summary:",
        "{{summary}}",
        "",
        "New lines of conversation:",
        "{{latestMessages}}",
        "",
        "New summary:"
})
public class SummarizerPrompt {

    private final String summary;
    private final String latestMessages;

    public SummarizerPrompt(String summary, String latestMessages) {
        this.summary = summary;
        this.latestMessages = latestMessages;
    }
}
