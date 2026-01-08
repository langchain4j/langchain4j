package dev.langchain4j.model.anthropic;

import org.jspecify.annotations.Nullable;

/**
 * Individual result within a batch response.
 *
 * @param customId the custom ID provided when creating the request
 * @param result the successful result, or null if the request failed
 * @param error error message if the request failed, or null if successful
 * @param resultType the type of result: "succeeded", "errored", "canceled", or "expired"
 * @param <T> the type of the result
 */
public record AnthropicBatchIndividualResult<T>(
        String customId, @Nullable T result, @Nullable String error, String resultType) {

    /**
     * @return true if this request succeeded
     */
    public boolean isSucceeded() {
        return "succeeded".equals(resultType);
    }

    /**
     * @return true if this request errored
     */
    public boolean isErrored() {
        return "errored".equals(resultType);
    }

    /**
     * @return true if this request was canceled
     */
    public boolean isCanceled() {
        return "canceled".equals(resultType);
    }

    /**
     * @return true if this request expired
     */
    public boolean isExpired() {
        return "expired".equals(resultType);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String customId;
        private T result;
        private String error;
        private String resultType;

        public Builder<T> customId(String customId) {
            this.customId = customId;
            return this;
        }

        public Builder<T> result(T result) {
            this.result = result;
            return this;
        }

        public Builder<T> error(String error) {
            this.error = error;
            return this;
        }

        public Builder<T> resultType(String resultType) {
            this.resultType = resultType;
            return this;
        }

        public AnthropicBatchIndividualResult<T> build() {
            return new AnthropicBatchIndividualResult<>(customId, result, error, resultType);
        }
    }
}
