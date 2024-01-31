package dev.langchain4j.service;

import dev.langchain4j.agent.tool.DefaultToolExecutor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.services.AiServicesFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;

/**
 * AI Services provide a simpler and more flexible alternative to chains.
 * You can define your own API (a Java interface with one or more methods),
 * and AiServices will provide an implementation for it (we call this "AI Service").
 * <p>
 * Currently, AI Services support:
 * <pre>
 * - Prompt templates for user and system messages using {@link UserMessage} and {@link SystemMessage}
 * - Structured prompts as method arguments (see {@link StructuredPrompt})
 * - Shared or per-user (see {@link MemoryId}) chat memory
 * - RAG (see {@link RetrievalAugmentor})
 * - Tools (see {@link Tool})
 * - Various return types (output parsers), see below
 * - Streaming (use {@link TokenStream} as a return type)
 * - Auto-moderation using {@link Moderate}
 * </pre>
 * <p>
 * Here is the simplest example of an AI Service:
 *
 * <pre>
 * interface Assistant {
 *
 *     String chat(String userMessage);
 * }
 *
 * Assistant assistant = AiServices.create(Assistant.class, model);
 *
 * String answer = assistant.chat("hello");
 * System.out.println(answer); // Hello, how can I help you today?
 * </pre>
 *
 * <pre>
 * The return type of methods in your AI Service can be any of the following:
 * - a {@code String} or an {@link AiMessage}, if you want to get the answer from the LLM as-is
 * - a {@code List<String>} or {@code Set<String>}, if you want to receive the answer as a collection of items or bullet points
 * - any {@code Enum} or a {@code boolean}, if you want to use the LLM for classification
 * - a primitive or boxed Java type: {@code int}, {@code Double}, etc., if you want to use the LLM for data extraction
 * - many default Java types: {@code Date}, {@code LocalDateTime}, {@code BigDecimal}, etc., if you want to use the LLM for data extraction
 * - any custom POJO, if you want to use the LLM for data extraction
 * </pre>
 * <p>
 * Let's see how we can classify the sentiment of a text:
 * <pre>
 * enum Sentiment {
 *     POSITIVE, NEUTRAL, NEGATIVE
 * }
 *
 * interface SentimentAnalyzer {
 *
 *     {@code @UserMessage}("Analyze sentiment of {{it}}")
 *     Sentiment analyzeSentimentOf(String text);
 * }
 *
 * SentimentAnalyzer assistant = AiServices.create(SentimentAnalyzer.class, model);
 *
 * Sentiment sentiment = analyzeSentimentOf.chat("I love you");
 * System.out.println(sentiment); // POSITIVE
 * </pre>
 * <p>
 * As demonstrated, you can put {@link UserMessage} and {@link SystemMessage} annotations above a method to define
 * templates for user and system messages, respectively.
 * In this example, the special {@code {{it}}} prompt template variable is used because there's only one method parameter.
 * However, you can use more parameters as demonstrated in the following example:
 * <pre>
 * interface Translator {
 *
 *     {@code @SystemMessage}("You are a professional translator into {{language}}")
 *     {@code @UserMessage}("Translate the following text: {{text}}")
 *     String translate(@V("text") String text, @V("language") String language);
 * }
 * </pre>
 * <p>
 * See more examples <a href="https://github.com/langchain4j/langchain4j-examples/tree/main/other-examples/src/main/java">here</a>.
 *
 * @param <T> The interface for which AiServices will provide an implementation.
 */
public abstract class AiServices<T> {

    protected static final String DEFAULT = "default";

    protected final AiServiceContext context;

    protected AiServices(AiServiceContext context) {
        this.context = context;
    }

    /**
     * Creates an AI Service (an implementation of the provided interface), that is backed by the provided chat model.
     * This convenience method can be used to create simple AI Services.
     * For more complex cases, please use {@link #builder}.
     *
     * @param aiService         The class of the interface to be implemented.
     * @param chatLanguageModel The chat model to be used under the hood.
     * @return An instance of the provided interface, implementing all its defined methods.
     */
    public static <T> T create(Class<T> aiService, ChatLanguageModel chatLanguageModel) {
        return builder(aiService)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    /**
     * Creates an AI Service (an implementation of the provided interface), that is backed by the provided streaming chat model.
     * This convenience method can be used to create simple AI Services.
     * For more complex cases, please use {@link #builder}.
     *
     * @param aiService                  The class of the interface to be implemented.
     * @param streamingChatLanguageModel The streaming chat model to be used under the hood.
     *                                   The return type of all methods should be {@link TokenStream}.
     * @return An instance of the provided interface, implementing all its defined methods.
     */
    public static <T> T create(Class<T> aiService, StreamingChatLanguageModel streamingChatLanguageModel) {
        return builder(aiService)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .build();
    }

    /**
     * Begins the construction of an AI Service.
     *
     * @param aiService The class of the interface to be implemented.
     * @return builder
     */
    public static <T> AiServices<T> builder(Class<T> aiService) {
        AiServiceContext context = new AiServiceContext(aiService);
        return ServiceHelper.loadFactoryService(
                AiServicesFactory.class,
                f -> f.create(context),
                () -> new DefaultAiServices<>(context));
    }

    /**
     * Configures chat model that will be used under the hood of the AI Service.
     * <p>
     * Either {@link ChatLanguageModel} or {@link StreamingChatLanguageModel} should be configured,
     * but not both at the same time.
     *
     * @param chatLanguageModel Chat model that will be used under the hood of the AI Service.
     * @return builder
     */
    public AiServices<T> chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        context.chatModel = chatLanguageModel;
        return this;
    }

    /**
     * Configures streaming chat model that will be used under the hood of the AI Service.
     * The methods of the AI Service must return a {@link TokenStream} type.
     * <p>
     * Either {@link ChatLanguageModel} or {@link StreamingChatLanguageModel} should be configured,
     * but not both at the same time.
     *
     * @param streamingChatLanguageModel Streaming chat model that will be used under the hood of the AI Service.
     * @return builder
     */
    public AiServices<T> streamingChatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        context.streamingChatModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * Configures the chat memory that will be used to preserve conversation history between method calls.
     * <p>
     * Unless a {@link ChatMemory} or {@link ChatMemoryProvider} is configured, all method calls will be independent of each other.
     * In other words, the LLM will not remember the conversation from the previous method calls.
     * <p>
     * The same {@link ChatMemory} instance will be used for every method call.
     * <p>
     * If you want to have a separate {@link ChatMemory} for each user/conversation, configure {@link #chatMemoryProvider} instead.
     * <p>
     * Either a {@link ChatMemory} or a {@link ChatMemoryProvider} can be configured, but not both simultaneously.
     *
     * @param chatMemory An instance of chat memory to be used by the AI Service.
     * @return builder
     */
    public AiServices<T> chatMemory(ChatMemory chatMemory) {
        context.chatMemories = new ConcurrentHashMap<>();
        context.chatMemories.put(DEFAULT, chatMemory);
        return this;
    }

    /**
     * Configures the chat memory provider, which provides a dedicated instance of {@link ChatMemory} for each user/conversation.
     * To distinguish between users/conversations, one of the method's arguments should be a memory ID (of any data type)
     * annotated with {@link MemoryId}.
     * For each new (previously unseen) memoryId, an instance of {@link ChatMemory} will be automatically obtained
     * by invoking {@link ChatMemoryProvider#get(Object id)}.
     * Example:
     * <pre>
     * interface Assistant {
     *
     *     String chat(@MemoryId int memoryId, @UserMessage String message);
     * }
     * </pre>
     * If you prefer to use the same (shared) {@link ChatMemory} for all users/conversations, configure a {@link #chatMemory} instead.
     * <p>
     * Either a {@link ChatMemory} or a {@link ChatMemoryProvider} can be configured, but not both simultaneously.
     *
     * @param chatMemoryProvider The provider of a {@link ChatMemory} for each new user/conversation.
     * @return builder
     */
    public AiServices<T> chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        context.chatMemories = new ConcurrentHashMap<>();
        context.chatMemoryProvider = chatMemoryProvider;
        return this;
    }

    /**
     * Configures a moderation model to be used for automatic content moderation.
     * If a method in the AI Service is annotated with {@link Moderate}, the moderation model will be invoked
     * to check the user content for any inappropriate or harmful material.
     *
     * @param moderationModel The moderation model to be used for content moderation.
     * @return builder
     * @see Moderate
     */
    public AiServices<T> moderationModel(ModerationModel moderationModel) {
        context.moderationModel = moderationModel;
        return this;
    }

    /**
     * Configures the tools that the LLM can use.
     * A {@link ChatMemory} that can hold at least 3 messages is required for the tools to work properly.
     *
     * @param objectsWithTools One or more objects whose methods are annotated with {@link Tool}.
     *                         All these tools (methods annotated with {@link Tool}) will be accessible to the LLM.
     *                         Note that inherited methods are ignored.
     * @return builder
     * @see Tool
     */
    public AiServices<T> tools(Object... objectsWithTools) {
        return tools(Arrays.asList(objectsWithTools));
    }

    /**
     * Configures the tools that the LLM can use.
     * A {@link ChatMemory} that can hold at least 3 messages is required for the tools to work properly.
     *
     * @param objectsWithTools A list of objects whose methods are annotated with {@link Tool}.
     *                         All these tools (methods annotated with {@link Tool}) are accessible to the LLM.
     *                         Note that inherited methods are ignored.
     * @return builder
     * @see Tool
     */
    public AiServices<T> tools(List<Object> objectsWithTools) {
        context.toolSpecifications = new ArrayList<>();
        context.toolExecutors = new HashMap<>();

        for (Object objectWithTool : objectsWithTools) {
            for (Method method : objectWithTool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification toolSpecification = toolSpecificationFrom(method);
                    context.toolSpecifications.add(toolSpecification);
                    context.toolExecutors.put(toolSpecification.name(), new DefaultToolExecutor(objectWithTool, method));
                }
            }
        }

        return this;
    }

    /**
     * Deprecated. Use {@link #contentRetriever(ContentRetriever)}
     * (e.g. {@link EmbeddingStoreContentRetriever}) instead.
     * <br>
     * Configures a retriever that will be invoked on every method call to fetch relevant information
     * related to the current user message from an underlying source (e.g., embedding store).
     * This relevant information is automatically injected into the message sent to the LLM.
     *
     * @param retriever The retriever to be used by the AI Service.
     * @return builder
     */
    @Deprecated
    public AiServices<T> retriever(Retriever<TextSegment> retriever) {
        if (retriever != null) {
            return contentRetriever(retriever.toContentRetriever());
        }
        return this;
    }

    /**
     * Configures a content retriever to be invoked on every method call for retrieving relevant content
     * related to the user's message from an underlying data source
     * (e.g., an embedding store in the case of an {@link EmbeddingStoreContentRetriever}).
     * The retrieved relevant content is then automatically incorporated into the message sent to the LLM.
     * <br>
     * This method provides a straightforward approach for those who do not require
     * a customized {@link RetrievalAugmentor}.
     * It configures a {@link DefaultRetrievalAugmentor} with the provided {@link ContentRetriever}.
     *
     * @param contentRetriever The content retriever to be used by the AI Service.
     * @return builder
     */
    public AiServices<T> contentRetriever(ContentRetriever contentRetriever) {
        context.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(ensureNotNull(contentRetriever, "contentRetriever"))
                .build();
        return this;
    }

    /**
     * Configures a retrieval augmentor to be invoked on every method call.
     *
     * @param retrievalAugmentor The retrieval augmentor to be used by the AI Service.
     * @return builder
     */
    public AiServices<T> retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
        context.retrievalAugmentor = ensureNotNull(retrievalAugmentor, "retrievalAugmentor");
        return this;
    }

    /**
     * Constructs and returns the AI Service.
     *
     * @return An instance of the AI Service implementing the specified interface.
     */
    public abstract T build();

    protected void performBasicValidation() {
        if (context.chatModel == null && context.streamingChatModel == null) {
            throw illegalConfiguration("Please specify either chatLanguageModel or streamingChatLanguageModel");
        }

        if (context.toolSpecifications != null && !context.hasChatMemory()) {
            throw illegalConfiguration(
                    "Please set up chatMemory or chatMemoryProvider in order to use tools. "
                            + "A ChatMemory that can hold at least 3 messages is required for the tools to work properly. "
                            + "While the LLM can technically execute a tool without chat memory, if it only receives the " +
                            "result of the tool's execution without the initial message from the user, it won't interpret " +
                            "the result properly."
            );
        }
    }

    public static List<ChatMessage> removeToolMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(it -> !(it instanceof ToolExecutionResultMessage))
                .filter(it -> !(it instanceof AiMessage && ((AiMessage) it).hasToolExecutionRequests()))
                .collect(toList());
    }

    public static void verifyModerationIfNeeded(Future<Moderation> moderationFuture) {
        if (moderationFuture != null) {
            try {
                Moderation moderation = moderationFuture.get();
                if (moderation.flagged()) {
                    throw new ModerationException(String.format("Text \"%s\" violates content policy", moderation.flaggedText()));
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
