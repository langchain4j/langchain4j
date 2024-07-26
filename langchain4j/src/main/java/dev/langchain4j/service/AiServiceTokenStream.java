package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;

public class AiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messagesToSend;
    private final List<Content> content;
    private final AiServiceContext context;
    private final Object memoryId;

    private Consumer<String> tokenHandler;
    private Consumer<List<Content>> contentHandler;
    private Consumer<Throwable> errorHandler;
    private Consumer<Response<AiMessage>> completionHandler;

    public AiServiceTokenStream(List<ChatMessage> messagesToSend, List<Content> content, AiServiceContext context, Object memoryId) {
        this.messagesToSend = ensureNotEmpty(messagesToSend, "messagesToSend");
        this.content = content;
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");
        ensureNotNull(context.streamingChatModel, "streamingChatModel");
    }

    @Override
    public TokenStream onNext(Consumer<String> tokenHandler) {
        this.tokenHandler = tokenHandler;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentHandler) {
        this.contentHandler = contentHandler;
        return this;
    }

    @Override
    public TokenStream onComplete(Consumer<Response<AiMessage>> completionHandler) {
        this.completionHandler = completionHandler;
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        this.errorHandler = null;
        return this;
    }

    @Override
    public void start() {

        AiServiceStreamingResponseHandler handler = new AiServiceStreamingResponseHandler(
                context,
                memoryId,
                tokenHandler,
                completionHandler,
                errorHandler,
                initTemporaryMemory(context, messagesToSend),
                new TokenUsage()
        );

        if (contentHandler != null && content != null) {
            contentHandler.accept(content);
        }

        if (context.toolSpecifications != null) {
            context.streamingChatModel.generate(messagesToSend, context.toolSpecifications, handler);
        } else {
            context.streamingChatModel.generate(messagesToSend, handler);
        }
    }

    private List<ChatMessage> initTemporaryMemory(AiServiceContext context, List<ChatMessage> messagesToSend) {
        if (context.hasChatMemory()) {
            return emptyList();
        } else {
            return new ArrayList<>(messagesToSend);
        }
    }
}
