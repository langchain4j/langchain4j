package dev.langchain4j.model.workersai.client;

/**
 * Wrapper for the text completion response.
 */
public class WorkersAiTextCompletionResponse extends ApiResponse<dev.langchain4j.model.workersai.client.WorkersAiTextCompletionResponse.TextResponse> {

    /**
     * Default constructor.
     */
    public WorkersAiTextCompletionResponse() {
    }

    /**
     * Wrapper for the text completion response.
     */
    public static class TextResponse {

        /**
         * The generated text.
         */
        private String response;

        /**
         * Default constructor.
         */
        public TextResponse() {
        }

        public String getResponse() {
            return this.response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof TextResponse)) return false;
            final TextResponse other = (TextResponse) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$response = this.getResponse();
            final Object other$response = other.getResponse();
            if (this$response == null ? other$response != null : !this$response.equals(other$response)) return false;
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
            return result;
        }

        public String toString() {
            return "WorkersAiTextCompletionResponse.TextResponse(response=" + this.getResponse() + ")";
        }
    }
}
