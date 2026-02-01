package dev.langchain4j.model.workersai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wrapper for the text completion response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkersAiTextCompletionResponse
        extends ApiResponse<dev.langchain4j.model.workersai.client.WorkersAiTextCompletionResponse.TextResponse> {

    /**
     * Default constructor.
     */
    public WorkersAiTextCompletionResponse() {}

    /**
     * Represents the text portion of the completion response from the Workers AI API.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextResponse {

        /**
         * The generated text.
         */
        @JsonProperty("response")
        private String response;

        /**
         * The token usage statistics.
         */
        @JsonProperty("usage")
        private Usage usage;

        /**
         * The reason the generation finished.
         */
        @JsonProperty("finish_reason")
        private String finishReason;

        /**
         * Default constructor.
         */
        public TextResponse() {}

        public String getResponse() {
            return this.response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public Usage getUsage() {
            return this.usage;
        }

        public void setUsage(Usage usage) {
            this.usage = usage;
        }

        public String getFinishReason() {
            return this.finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof TextResponse)) return false;
            final TextResponse other = (TextResponse) o;
            if (!other.canEqual((Object) this)) return false;

            final Object this$response = this.getResponse();
            final Object other$response = other.getResponse();
            if (this$response == null ? other$response != null : !this$response.equals(other$response)) return false;

            final Object this$usage = this.getUsage();
            final Object other$usage = other.getUsage();
            if (this$usage == null ? other$usage != null : !this$usage.equals(other$usage)) return false;

            final Object this$finishReason = this.getFinishReason();
            final Object other$finishReason = other.getFinishReason();
            if (this$finishReason == null ? other$finishReason != null : !this$finishReason.equals(other$finishReason))
                return false;

            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof TextResponse;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;

            final Object $response = this.getResponse();
            result = result * PRIME + ($response == null ? 43 : $response.hashCode());

            final Object $usage = this.getUsage();
            result = result * PRIME + ($usage == null ? 43 : $usage.hashCode());

            final Object $finishReason = this.getFinishReason();
            result = result * PRIME + ($finishReason == null ? 43 : $finishReason.hashCode());

            return result;
        }

        @Override
        public String toString() {
            return "WorkersAiTextCompletionResponse.TextResponse(response=" + this.getResponse() + ", usage="
                    + this.getUsage() + ", finishReason="
                    + this.getFinishReason() + ")";
        }
    }
    /**
     * Wrapper for usage statistics.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        public Usage() {}

        public Integer getPromptTokens() {
            return this.promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return this.completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return this.totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof Usage)) return false;
            final Usage other = (Usage) o;
            if (!other.canEqual((Object) this)) return false;

            final Object this$promptTokens = this.getPromptTokens();
            final Object other$promptTokens = other.getPromptTokens();
            if (this$promptTokens == null ? other$promptTokens != null : !this$promptTokens.equals(other$promptTokens))
                return false;

            final Object this$completionTokens = this.getCompletionTokens();
            final Object other$completionTokens = other.getCompletionTokens();
            if (this$completionTokens == null
                    ? other$completionTokens != null
                    : !this$completionTokens.equals(other$completionTokens)) return false;

            final Object this$totalTokens = this.getTotalTokens();
            final Object other$totalTokens = other.getTotalTokens();
            if (this$totalTokens == null ? other$totalTokens != null : !this$totalTokens.equals(other$totalTokens))
                return false;

            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof Usage;
        }

        @Override
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;

            final Object $promptTokens = this.getPromptTokens();
            result = result * PRIME + ($promptTokens == null ? 43 : $promptTokens.hashCode());

            final Object $completionTokens = this.getCompletionTokens();
            result = result * PRIME + ($completionTokens == null ? 43 : $completionTokens.hashCode());

            final Object $totalTokens = this.getTotalTokens();
            result = result * PRIME + ($totalTokens == null ? 43 : $totalTokens.hashCode());

            return result;
        }

        public String toString() {
            return "WorkersAiTextCompletionResponse.Usage(promptTokens=" + this.getPromptTokens()
                    + ", completionTokens="
                    + this.getCompletionTokens() + ", totalTokens="
                    + this.getTotalTokens() + ")";
        }
    }
}
