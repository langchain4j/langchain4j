package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Context {

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
            super(agenticScope, summarizingContextProvider(chatModel, agentNames));
        }

        private static Function<AgenticScope, String> summarizingContextProvider(
                ChatModel chatModel, String... agentNames) {
            AtomicReference<ContextSummarizer> summarizer = new AtomicReference<>();
            return c -> {
                String context = c.contextAsConversation(agentNames);
                if (context.isBlank()) {
                    return context;
                }
                return summarizer
                        .updateAndGet(current -> current != null
                                ? current
                                : AiServices.builder(ContextSummarizer.class)
                                        .chatModel(chatModel)
                                        .build())
                        .summarize(context)
                        .getSummary();
            };
        }
    }
}
