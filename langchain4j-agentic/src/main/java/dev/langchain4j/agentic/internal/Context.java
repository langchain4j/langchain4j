package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Context {

    private static final String SUMMARIZER_PROMPT = """
            Create a short summary of the following conversation between one or more AI agents and a user.
            Mention all the agents involved in the conversation.
            Do not provide any additional information, just the summary.
            The user conversation is: '%s'.
            """;

    public interface ContextSummarizer {

        String summarize(String conversation);
    }

    /**
     * Creates a new stateless summarizer that sends the summarization prompt to the given {@link ChatModel}
     * and returns the text of its answer.
     *
     * @throws IllegalConfigurationException if {@code chatModel} is {@code null}
     */
    public static ContextSummarizer createSummarizer(ChatModel chatModel) {
        if (chatModel == null) {
            throw new IllegalConfigurationException("A ChatModel is required to summarize agentic context.");
        }
        return conversation -> chatModel.chat(summarizationPrompt(conversation));
    }

    /**
     * Creates a new stateless summarizer that sends the summarization prompt to the given {@link StreamingChatModel}
     * and returns the text of its answer. The returned summarizer blocks until the streamed answer is complete.
     *
     * @throws IllegalConfigurationException if {@code streamingChatModel} is {@code null}
     */
    public static ContextSummarizer createStreamingSummarizer(StreamingChatModel streamingChatModel) {
        if (streamingChatModel == null) {
            throw new IllegalConfigurationException("A StreamingChatModel is required to summarize agentic context.");
        }
        return conversation -> {
            CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
            streamingChatModel.chat(summarizationPrompt(conversation), new StreamingChatResponseHandler() {
                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    futureResponse.complete(completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    futureResponse.completeExceptionally(error);
                }
            });
            return DelayedResponse.join(futureResponse).aiMessage().text();
        };
    }

    private static String summarizationPrompt(String conversation) {
        return SUMMARIZER_PROMPT.formatted(conversation);
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

        /**
         * Creates a summarizer that sends the summarization prompt to the given {@link ChatModel}.
         *
         * @throws IllegalConfigurationException if {@code chatModel} is {@code null}
         */
        public Summarizer(AgenticScope agenticScope, ChatModel chatModel, String... agentNames) {
            this(agenticScope, createSummarizer(chatModel), agentNames);
        }

        /**
         * Creates a summarizer backed by a pre-built {@link ContextSummarizer}, typically one shared
         * by the caller across agents or invocations using the same model.
         */
        public static Summarizer withSummarizer(
                AgenticScope agenticScope, ContextSummarizer summarizer, String... agentNames) {
            return new Summarizer(agenticScope, summarizer, agentNames);
        }

        /**
         * Creates a summarizer whose {@link ChatModel} is resolved through the given provider at
         * summarization time, so it follows model changes across scopes or invocations.
         */
        public static Summarizer withChatModelProvider(
                AgenticScope agenticScope, Function<AgenticScope, ChatModel> chatModelProvider, String... agentNames) {
            return new Summarizer(
                    agenticScope,
                    modelBasedSummarizer(agenticScope, chatModelProvider, Context::createSummarizer, "chat model"),
                    agentNames);
        }

        /**
         * Creates a summarizer whose {@link StreamingChatModel} is resolved through the given provider at
         * summarization time, so it follows model changes across scopes or invocations.
         */
        public static Summarizer withStreamingChatModelProvider(
                AgenticScope agenticScope,
                Function<AgenticScope, StreamingChatModel> streamingChatModelProvider,
                String... agentNames) {
            return new Summarizer(
                    agenticScope,
                    modelBasedSummarizer(
                            agenticScope,
                            streamingChatModelProvider,
                            Context::createStreamingSummarizer,
                            "streaming chat model"),
                    agentNames);
        }

        private Summarizer(AgenticScope agenticScope, ContextSummarizer summarizer, String... agentNames) {
            super(agenticScope, c -> {
                String context = c.contextAsConversation(agentNames);
                return context.isBlank() ? context : summarizer.summarize(context);
            });
        }

        private static <M> ContextSummarizer modelBasedSummarizer(
                AgenticScope agenticScope,
                Function<AgenticScope, M> modelProvider,
                Function<M, ContextSummarizer> summarizerFactory,
                String modelType) {
            return conversation -> {
                M model = modelProvider.apply(agenticScope);
                if (model == null) {
                    throw new IllegalConfigurationException(
                            "The " + modelType + " provider returned null while summarizing agentic context.");
                }
                return summarizerFactory.apply(model).summarize(conversation);
            };
        }
    }
}
