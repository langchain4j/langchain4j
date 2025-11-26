package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.chat.response.StreamingHandle;
import org.reactivestreams.Subscription;

/**
 * @since 1.8.0
 */
class BedrockStreamingHandle implements StreamingHandle {

    private final Subscription subscription;
    private volatile boolean isCancelled;

    BedrockStreamingHandle(Subscription subscription) {
        this.subscription = ensureNotNull(subscription, "subscription");
    }

    @Override
    public void cancel() {
        isCancelled = true;
        try {
            subscription.cancel();
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }
}
