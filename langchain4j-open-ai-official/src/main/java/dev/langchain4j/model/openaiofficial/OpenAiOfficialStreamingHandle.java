package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.openai.core.http.AsyncStreamResponse;
import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * @since 1.8.0
 */
class OpenAiOfficialStreamingHandle implements StreamingHandle {

    private final AsyncStreamResponse<?> asyncStreamResponse;
    private volatile boolean isCancelled;

    OpenAiOfficialStreamingHandle(AsyncStreamResponse<?> asyncStreamResponse) {
        this.asyncStreamResponse = ensureNotNull(asyncStreamResponse, "asyncStreamResponse");
    }

    @Override
    public void cancel() {
        isCancelled = true;
        try {
            asyncStreamResponse.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }
}
