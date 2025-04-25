package dev.langchain4j.service;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatExecutor;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Internal
public class AiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final List<Content> retrievedContents;
    private final AiServiceContext context;
    private final Object memoryId;
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    private Consumer<String> partialResponseHandler;
    private Consumer<List<Content>> contentsHandler;
    private Consumer<ToolExecution> toolExecutionHandler;
    private Consumer<ChatResponse> completeResponseHandler;
    private Consumer<Throwable> errorHandler;

    private int onPartialResponseInvoked;
    private int onCompleteResponseInvoked;
    private int onRetrievedInvoked;
    private int onToolExecutedInvoked;
    private int onErrorInvoked;
    private int ignoreErrorsInvoked;

    /**
     * Creates a new instance of {@link AiServiceTokenStream} with the given parameters.
     *
     * @param parameters the parameters for creating the token stream
     */
    public AiServiceTokenStream(AiServiceTokenStreamParameters parameters) {
        ensureNotNull(parameters, "parameters");
        this.messages = parameters.messages();
        this.toolSpecifications = parameters.toolSpecifications();
        this.toolExecutors = parameters.toolExecutors();
        this.retrievedContents = parameters.gretrievedContents();
        this.context = parameters.context();
        this.memoryId = parameters.memoryId();
        this.commonGuardrailParams = parameters.commonGuardrailParams();
        this.methodKey = parameters.methodKey();
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        this.onPartialResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentsHandler) {
        this.contentsHandler = contentsHandler;
        this.onRetrievedInvoked++;
        return this;
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecutionHandler) {
        this.toolExecutionHandler = toolExecutionHandler;
        this.onToolExecutedInvoked++;
        return this;
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> completionHandler) {
        this.completeResponseHandler = completionHandler;
        this.onCompleteResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        this.onErrorInvoked++;
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        this.errorHandler = null;
        this.ignoreErrorsInvoked++;
        return this;
    }

    @Override
    public void start() {
        validateConfiguration();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();

        ChatExecutor chatExecutor = ChatExecutor.builder()
                .chatRequest(chatRequest)
                .chatModel(context.chatModel)
                .build();

        var handler = new AiServiceStreamingResponseHandler(
                chatExecutor,
                context,
                memoryId,
                partialResponseHandler,
                toolExecutionHandler,
                completeResponseHandler,
                errorHandler,
                initTemporaryMemory(context, messages),
                new TokenUsage(),
                toolSpecifications,
                toolExecutors,
                commonGuardrailParams,
                methodKey);

        if (contentsHandler != null && retrievedContents != null) {
            contentsHandler.accept(retrievedContents);
        }

        context.streamingChatModel.chat(chatRequest, handler);
    }

    private void validateConfiguration() {
        if (onPartialResponseInvoked != 1) {
            throw new IllegalConfigurationException("onPartialResponse must be invoked on TokenStream exactly 1 time");
        }
        if (onCompleteResponseInvoked > 1) {
            throw new IllegalConfigurationException("onCompleteResponse can be invoked on TokenStream at most 1 time");
        }
        if (onRetrievedInvoked > 1) {
            throw new IllegalConfigurationException("onRetrieved can be invoked on TokenStream at most 1 time");
        }
        if (onToolExecutedInvoked > 1) {
            throw new IllegalConfigurationException("onToolExecuted can be invoked on TokenStream at most 1 time");
        }
        if (onErrorInvoked + ignoreErrorsInvoked != 1) {
            throw new IllegalConfigurationException(
                    "One of [onError, ignoreErrors] " + "must be invoked on TokenStream exactly 1 time");
        }
    }

    private ChatMemory initTemporaryMemory(AiServiceContext context, List<ChatMessage> messagesToSend) {
        var chatMemory = new InMemoryChatMemory();

        if (!context.hasChatMemory()) {
            chatMemory.add(messagesToSend);
        }

        return chatMemory;
    }

    /**
     * Implementation of {@link ChatMemory} that stores state in-memory.
     * <p>
     * This storage mechanism is transient and does not persist data across application restarts.
     */
    private static class InMemoryChatMemory implements ChatMemory {
        private final ChatMemoryStore store = new InMemoryChatMemoryStore();
        private final Object id;

        public InMemoryChatMemory(Object id) {
            this.id = ensureNotNull(id, "id");
        }

        public InMemoryChatMemory() {
            this(UUID.randomUUID());
        }

        @Override
        public Object id() {
            return this.id;
        }

        @Override
        public void add(ChatMessage message) {
            var messages = messages();

            if (message.type() == ChatMessageType.SYSTEM) {
                findSystemMessage(messages)
                        .filter(message::equals) // Do not add the same system message
                        .ifPresent(messages::remove); // Need to replace the existing system message
            }

            messages.add(message);
            this.store.updateMessages(this.id, messages);
        }

        private static Optional<dev.langchain4j.data.message.SystemMessage> findSystemMessage(
                List<ChatMessage> messages) {
            return messages.stream()
                    .filter(dev.langchain4j.data.message.SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .findAny();
        }

        @Override
        public List<ChatMessage> messages() {
            return new LinkedList<>(this.store.getMessages(this.id));
        }

        @Override
        public void clear() {
            this.store.deleteMessages(this.id);
        }
    }
}
