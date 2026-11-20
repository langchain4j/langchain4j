package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.logIgnoredError;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.internal.HandlerWithoutInvocation;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModelHelper;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A concrete implementation of the {@link ChatExecutor} interface that executes
 * chat requests using a specified {@link StreamingChatModel}. It then executes the requests as if it were
 * synchronous, essentially transforming a streaming request to a synchronous request
 *
 * This class utilizes a {@link ChatRequest} to encapsulate the input messages
 *  and parameters and delegates the execution of the chat to the provided {@link StreamingChatModel}.
 *
 *  Instances of this class are immutable and are typically instantiated using
 *  the {@link StreamingToSynchronousBuilder}.
 */
@Internal
final class StreamingToSynchronousChatExecutor extends AbstractChatExecutor {
    private final StreamingChatModel streamingChatModel;
    private final Consumer<Throwable> errorHandler;

    protected StreamingToSynchronousChatExecutor(StreamingToSynchronousBuilder builder) {
        super(builder);

        this.streamingChatModel = ensureNotNull(builder.streamingChatModel, "streamingChatModel");
        this.errorHandler = builder.errorHandler;
    }

    @Override
    protected ChatResponse execute(ChatRequest chatRequest) {
        var responseHandler = new StreamingToSyncResponseHandler(this.errorHandler);
        CurrentInvocation.runWith(invocationContext, () -> this.streamingChatModel.chat(chatRequest, HandlerWithoutInvocation.wrap(responseHandler)));

        return Optional.ofNullable(responseHandler.getResponse()).orElseGet(ChatResponse.builder()::build);
    }

    @Override
    protected CompletableFuture<ChatResponse> executeAsync(ChatRequest chatRequest) {
        Consumer<Throwable> errorHandlerWithoutInvocation = errorHandler == null
                ? null
                : error -> CurrentInvocation.runWithout(() -> errorHandler.accept(error));
        return CurrentInvocation.callWith(
                invocationContext,
                () -> StreamingChatModelHelper.chatAsync(
                        this.streamingChatModel, chatRequest, errorHandlerWithoutInvocation));
    }

    private static class StreamingToSyncResponseHandler implements StreamingChatResponseHandler {
        private static final Logger LOG = LoggerFactory.getLogger(StreamingToSyncResponseHandler.class);
        private final Consumer<Throwable> errorHandler;
        private final CountDownLatch latch = new CountDownLatch(1);
        // the first complete response or error is the outcome of the request, as for the asynchronous execution
        private final AtomicReference<Outcome> outcome = new AtomicReference<>();

        StreamingToSyncResponseHandler(Consumer<Throwable> errorHandler) {
            this.errorHandler = errorHandler;
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            outcome.compareAndSet(null, new Outcome(completeResponse, null));
            this.latch.countDown();
        }

        private void waitForCompletion() {
            try {
                this.latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        ChatResponse getResponse() {
            waitForCompletion();
            Outcome result = this.outcome.get();
            Throwable failure = result.error();
            if (failure == null) {
                return result.response();
            }
            // like the asynchronous execution, whose future fails after the error handler was called
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error e) {
                throw e;
            }
            throw new RuntimeException(failure);
        }

        @Override
        public void onError(Throwable error) {
            try {
                if (errorHandler != null) {
                    try {
                        errorHandler.accept(error);
                    } catch (Exception e) {
                        LOG.error("While handling the following error...", error);
                        LOG.error("...the following error happened", e);
                    }
                }
            } finally {
                boolean first = outcome.compareAndSet(null, new Outcome(null, error));
                if (!first && errorHandler == null) {
                    logIgnoredError(error); // nobody else sees this error: the request already has its outcome
                }
                this.latch.countDown();
            }
        }

        private record Outcome(@Nullable ChatResponse response, @Nullable Throwable error) {}
    }
}
