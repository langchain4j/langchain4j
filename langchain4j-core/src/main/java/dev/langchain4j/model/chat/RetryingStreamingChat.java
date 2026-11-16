package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.RetryUtils.DEFAULT_RETRY_POLICY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.internal.Exceptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a streaming chat request and retries it while nothing has been delivered to the user yet.
 * <p>
 * A failed attempt is retried only if all of these hold:
 * <ul>
 *     <li>no partial response, partial thinking, partial tool call, complete tool call or complete response
 *         was passed to the handler,</li>
 *     <li>the handler's {@code onUnmappedRawEvent} did not throw,</li>
 *     <li>the error is an {@link Exception} that is neither a {@link NonRetriableException} nor a
 *         {@link CancellationException} (the same rule as {@code RetryUtils} uses for synchronous models),</li>
 *     <li>retries remain.</li>
 * </ul>
 * The delay between attempts is scheduled rather than slept, because errors usually arrive on
 * HTTP client I/O threads that must not be blocked.
 * <p>
 * Once a retry is scheduled, the failed attempt is ignored: some providers keep calling the handler after
 * {@code onError} (for example, an SSE error event followed by the end of the stream).
 */
@Internal
class RetryingStreamingChat {

    private static final Logger log = LoggerFactory.getLogger(RetryingStreamingChat.class);

    private final BiConsumer<ChatRequest, StreamingChatResponseHandler> doChat;
    private final ChatRequest chatRequest;
    private final StreamingChatResponseHandler handler;
    private final int maxRetries;
    private final UUID invocationId;

    RetryingStreamingChat(
            BiConsumer<ChatRequest, StreamingChatResponseHandler> doChat,
            ChatRequest chatRequest,
            StreamingChatResponseHandler handler,
            int maxRetries,
            UUID invocationId) {
        this.doChat = doChat;
        this.chatRequest = chatRequest;
        this.handler = handler;
        this.maxRetries = maxRetries;
        this.invocationId = invocationId;
    }

    /**
     * Starts the first attempt on the calling thread.
     * An exception thrown synchronously by the first attempt propagates to the caller, as without retries,
     * unless the attempt has already reported an error that is being retried.
     */
    void start() {
        AttemptHandler firstAttempt = new AttemptHandler(0);
        try {
            doChat.accept(chatRequest, firstAttempt);
        } catch (RuntimeException e) {
            if (!firstAttempt.isReplaced()) {
                throw e;
            }
            // the retry will report the outcome to the handler, so the caller must not get an exception as well
            log.warn("Streaming request threw after its failure had already been scheduled for a retry", e);
        }
    }

    private void scheduleRetry(int failedAttempt, Throwable error) {
        log.warn(
                "Streaming request failed before any response was delivered. Retrying (%s of %s)"
                        .formatted(failedAttempt + 1, maxRetries),
                error);
        Executor executor = DefaultExecutorProvider.getDefaultExecutor();
        try {
            CompletableFuture.delayedExecutor(
                            DEFAULT_RETRY_POLICY.jitterDelayMillis(failedAttempt),
                            MILLISECONDS,
                            task -> submit(executor, task, error))
                    .execute(() -> runAttempt(failedAttempt + 1));
        } catch (RuntimeException e) {
            giveUp(error, e);
        }
    }

    // runs on the delayer thread later, so a rejection can't be caught by scheduleRetry
    private void submit(Executor executor, Runnable task, Throwable error) {
        try {
            executor.execute(task);
        } catch (RuntimeException e) {
            giveUp(error, e);
        }
    }

    private void giveUp(Throwable error, Throwable retryFailure) {
        error.addSuppressed(retryFailure);
        try {
            handler.onError(error);
        } catch (Exception e) {
            // this may run on the delayer thread, where an exception would be lost silently
            log.warn("Exception while calling onError() of the streaming response handler", e);
        }
    }

    private void runAttempt(int attempt) {
        AttemptHandler attemptHandler = new AttemptHandler(attempt);
        try {
            CurrentInvocation.runWithIdOrWithout(invocationId, () -> doChat.accept(chatRequest, attemptHandler));
        } catch (Throwable e) {
            // nobody is waiting for this thread, so the error goes to the handler instead
            if (attemptHandler.isFinished()) {
                log.warn("Streaming request threw after it had already finished", e);
            } else {
                attemptHandler.onError(e);
            }
        }
    }

    private static boolean isRetriable(Throwable error) {
        Throwable cause = Exceptions.unwrapCompletionException(error);
        return cause instanceof Exception
                && !(cause instanceof NonRetriableException)
                && !(cause instanceof CancellationException);
    }

    private enum AttemptState {
        /** Nothing has been passed to the handler yet. */
        RETRIABLE,
        /** Something has been passed to the handler, so this attempt can no longer be retried. */
        COMMITTED,
        /** A retry has been scheduled; this attempt is ignored from now on. */
        REPLACED
    }

    private class AttemptHandler implements StreamingChatResponseHandler {

        private final int attempt;
        private final AtomicReference<AttemptState> state = new AtomicReference<>(AttemptState.RETRIABLE);
        private final AtomicBoolean finished = new AtomicBoolean();

        AttemptHandler(int attempt) {
            this.attempt = attempt;
        }

        /**
         * Commits this attempt, unless it was already replaced by a retry.
         * A single atomic state makes "deliver" and "retry" mutually exclusive, even across threads.
         *
         * @return {@code true} if the callback may be passed to the handler
         */
        private boolean commit() {
            state.compareAndSet(AttemptState.RETRIABLE, AttemptState.COMMITTED);
            return state.get() == AttemptState.COMMITTED;
        }

        /**
         * @return {@code true} if this attempt was replaced by a retry or has already called
         *         {@code onCompleteResponse} or {@code onError} of the handler
         */
        boolean isFinished() {
            return finished.get() || isReplaced();
        }

        boolean isReplaced() {
            return state.get() == AttemptState.REPLACED;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            if (commit()) {
                handler.onPartialResponse(partialResponse);
            }
        }

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            if (commit()) {
                handler.onPartialResponse(partialResponse, context);
            }
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            if (commit()) {
                handler.onPartialThinking(partialThinking);
            }
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
            if (commit()) {
                handler.onPartialThinking(partialThinking, context);
            }
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            if (commit()) {
                handler.onPartialToolCall(partialToolCall);
            }
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
            if (commit()) {
                handler.onPartialToolCall(partialToolCall, context);
            }
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            // AI Services may start executing the tool here, so a retry could execute it twice
            if (commit()) {
                handler.onCompleteToolCall(completeToolCall);
            }
        }

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            // raw events do not commit the attempt: some providers send them before any content
            if (isReplaced()) {
                return;
            }
            try {
                handler.onUnmappedRawEvent(rawEvent);
            } catch (RuntimeException e) {
                // the provider will pass this to onError; a bug in the user's code must not cause a retry
                commit();
                throw e;
            }
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            if (commit()) {
                finished.set(true);
                handler.onCompleteResponse(completeResponse);
            }
        }

        @Override
        public void onError(Throwable error) {
            if (attempt < maxRetries
                    && isRetriable(error)
                    && state.compareAndSet(AttemptState.RETRIABLE, AttemptState.REPLACED)) {
                scheduleRetry(attempt, error);
            } else if (commit()) {
                // committed, so a later error of this attempt can't schedule a retry after the user got this one
                finished.set(true);
                handler.onError(error);
            }
        }
    }
}
