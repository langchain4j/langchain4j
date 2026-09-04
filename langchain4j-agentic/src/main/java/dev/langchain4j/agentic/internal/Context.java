package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class Context {

    // Reuse a stateless summarizer for each ChatModel.
    private static final Map<ChatModel, ContextSummarizer> SUMMARIZERS = new ConcurrentHashMap<>();

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

    static ContextSummarizer createSummarizer(ChatModel chatModel) {
        if (chatModel == null) {
            // Preserve AiServices' fail-fast validation for a missing model.
            return AiServices.builder(ContextSummarizer.class).build();
        }
        return SUMMARIZERS.computeIfAbsent(
                chatModel,
                model -> AiServices.builder(ContextSummarizer.class)
                        .chatModel(model)
                        .build());
    }

    public static class AgenticScopeContextGenerator implements UserMessageTransformer {
        private final AgenticScope agenticScope;
        private final Function<AgenticScope, String> contextProvider;

        public AgenticScopeContextGenerator(AgenticScope agenticScope, Function<AgenticScope, String> contextProvider) {
            this.agenticScope = agenticScope;
            this.contextProvider = contextProvider;
        }

        @Override
        public String transformUserMessage(String userMessage, Object memoryId) {
            if (agenticScope == null) {
                return userMessage;
            }
            String agenticScopeContext = contextProvider.apply(agenticScope);
            if (isNullOrBlank(agenticScopeContext)) {
                return userMessage;
            }
            return "Considering this context \"" + agenticScopeContext + "\"\n" + userMessage;
        }
    }

    public static class Summarizer extends AgenticScopeContextGenerator {
        public Summarizer(AgenticScope agenticScope, ChatModel chatModel, String... agentNames) {
            this(agenticScope, createSummarizer(chatModel), agentNames);
        }

        private Summarizer(AgenticScope agenticScope, ContextSummarizer summarizer, String... agentNames) {
            super(agenticScope, c -> {
                String context = c.contextAsConversation(agentNames);
                return context.isBlank()
                        ? context
                        : summarizer.summarize(context).getSummary();
            });
        }
    }
}
