package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import java.util.function.Function;

public class Context {

    private static final String SUMMARIZER_PROMPT = """
            Create a short summary of the following conversation between one or more AI agents and a user.
            Mention all the agents involved in the conversation.
            Do not provide any additional information, just the summary.
            The user conversation is: '{{it}}'.
            """;

    public interface ContextSummarizer {

        @UserMessage(SUMMARIZER_PROMPT)
        Summary summarize(String conversation);
    }

    public interface StreamingContextSummarizer {

        @UserMessage(SUMMARIZER_PROMPT)
        TokenStream summarize(String conversation);
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

    /**
     * Creates a new stateless summarizer AI service bound to the given {@link ChatModel}. The service
     * is not cached here: callers that build it once and share it across invocations (e.g.
     * {@code AgentBuilder} and {@code SupervisorAgentServiceImpl}) own the caching strategy.
     *
     * @throws IllegalConfigurationException if {@code chatModel} is {@code null}
     */
    public static ContextSummarizer createSummarizer(ChatModel chatModel) {
        if (chatModel == null) {
            throw new IllegalConfigurationException("A ChatModel is required to summarize agentic context.");
        }
        return AiServices.builder(ContextSummarizer.class).chatModel(chatModel).build();
    }

    /**
     * Creates a new stateless summarizer AI service bound to the given {@link StreamingChatModel}.
     * The returned summarizer blocks until the streamed summary is complete. The service is not cached here:
     * callers that build it once and share it across invocations own the caching strategy.
     *
     * @throws IllegalConfigurationException if {@code streamingChatModel} is {@code null}
     */
    public static ContextSummarizer createStreamingSummarizer(StreamingChatModel streamingChatModel) {
        if (streamingChatModel == null) {
            throw new IllegalConfigurationException("A StreamingChatModel is required to summarize agentic context.");
        }
        StreamingContextSummarizer streamingSummarizer = AiServices.builder(StreamingContextSummarizer.class)
                .streamingChatModel(streamingChatModel)
                .build();
        return conversation -> {
            Summary summary = new Summary();
            summary.setSummary(new StreamingResponse(streamingSummarizer.summarize(conversation)).blockingGet());
            return summary;
        };
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
         * Creates a summarizer that eagerly builds its own summarizer AI service from the given
         * {@link ChatModel}.
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
         * summarization time, so it follows model changes across scopes or invocations. The service
         * built for the last resolved model is cached per summarizer instance.
         */
        public static Summarizer withChatModelProvider(
                AgenticScope agenticScope, Function<AgenticScope, ChatModel> chatModelProvider, String... agentNames) {
            return new Summarizer(
                    agenticScope,
                    new ModelBasedSummarizer<>(agenticScope, chatModelProvider, Context::createSummarizer, "chat model"),
                    agentNames);
        }

        /**
         * Creates a summarizer whose {@link StreamingChatModel} is resolved through the given provider at
         * summarization time, so it follows model changes across scopes or invocations. The service
         * built for the last resolved model is cached per summarizer instance.
         */
        public static Summarizer withStreamingChatModelProvider(
                AgenticScope agenticScope,
                Function<AgenticScope, StreamingChatModel> streamingChatModelProvider,
                String... agentNames) {
            return new Summarizer(
                    agenticScope,
                    new ModelBasedSummarizer<>(
                            agenticScope,
                            streamingChatModelProvider,
                            Context::createStreamingSummarizer,
                            "streaming chat model"),
                    agentNames);
        }

        private Summarizer(AgenticScope agenticScope, ContextSummarizer summarizer, String... agentNames) {
            super(agenticScope, c -> {
                String context = c.contextAsConversation(agentNames);
                return context.isBlank()
                        ? context
                        : summarizer.summarize(context).getSummary();
            });
        }

        private static class ModelBasedSummarizer<M> implements ContextSummarizer {

            private final AgenticScope agenticScope;
            private final Function<AgenticScope, M> modelProvider;
            private final Function<M, ContextSummarizer> summarizerFactory;
            private final String modelType;

            private volatile CachedSummarizer<M> cachedSummarizer;

            private ModelBasedSummarizer(
                    AgenticScope agenticScope,
                    Function<AgenticScope, M> modelProvider,
                    Function<M, ContextSummarizer> summarizerFactory,
                    String modelType) {
                this.agenticScope = agenticScope;
                this.modelProvider = modelProvider;
                this.summarizerFactory = summarizerFactory;
                this.modelType = modelType;
            }

            @Override
            public Summary summarize(String conversation) {
                M model = modelProvider.apply(agenticScope);
                ContextSummarizer summarizer = summarizerFor(model);
                return summarizer.summarize(conversation);
            }

            private ContextSummarizer summarizerFor(M model) {
                if (model == null) {
                    throw new IllegalConfigurationException(
                            "The " + modelType + " provider returned null while summarizing agentic context.");
                }

                CachedSummarizer<M> cached = cachedSummarizer;
                if (cached != null && cached.model() == model) {
                    return cached.summarizer();
                }

                synchronized (this) {
                    cached = cachedSummarizer;
                    if (cached == null || cached.model() != model) {
                        cached = new CachedSummarizer<>(model, summarizerFactory.apply(model));
                        cachedSummarizer = cached;
                    }
                    return cached.summarizer();
                }
            }

            private record CachedSummarizer<M>(M model, ContextSummarizer summarizer) {}
        }
    }
}
