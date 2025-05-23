package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import java.util.function.Function;

public class Context {

    private static ContextSummarizer SUMMARIZER_INSTANCE;

    public interface ContextSummarizer {

        @UserMessage("""
            Create a short summary of the following conversation between one or more AI agents and a user.
            Mention all the agents involved in the conversation.
            Do not provide any additional information, just the summary.
            The user conversation is: '{{it}}'.
            """)
        Summary summarize(String conversation);
    }

    public static class Summary {
        private String summary;

        public String getSummary() {
            return summary;
        }

        public void setSummary(final String summary) {
            this.summary = summary;
        }
    }

    private static ContextSummarizer initSummarizer(ChatModel chatModel) {
        if (SUMMARIZER_INSTANCE == null) {
            SUMMARIZER_INSTANCE = AiServices.builder(ContextSummarizer.class)
                    .chatModel(chatModel)
                    .build();
        }
        return SUMMARIZER_INSTANCE;
    }

    public static class CognisphereContextGenerator implements UserMessageTransformer {
        private final Function<Cognisphere, String> contextProvider;

        public CognisphereContextGenerator(Function<Cognisphere, String> contextProvider) {
            this.contextProvider = contextProvider;
        }

        @Override
        public String transformUserMessage(String userMessage, Object memoryId) {
            Cognisphere cognisphere = Cognisphere.registry().get(memoryId);
            if (cognisphere == null) {
                return userMessage;
            }
            String cognisphereContext = contextProvider.apply(cognisphere);
            if (cognisphereContext == null || cognisphereContext.isBlank()) {
                return userMessage;
            }
            return "Considering this context \"" + cognisphereContext + "\"\n" + userMessage;
        }

    }

    public static class Summarizer extends CognisphereContextGenerator {
        public Summarizer(ChatModel chatModel, String... agentNames) {
            super(cognisphere -> {
                String context = cognisphere.contextAsConversation(agentNames);
                return context.isBlank() ? context : initSummarizer(chatModel).summarize(context).getSummary();
            });
        }
    }
}
