package dev.langchain4j.model.gpullama3;

/**
 * Utility class for parsing GPULlama3 model responses that contain thinking content.
 *
 * GPULlama3 models can generate responses with embedded thinking content in the format:
 * <think>thinking content...</think>actual response content
 *
 * This parser separates the thinking content from the actual response content.
 */
public class GPULlama3ResponseParser {

    /**
     * Represents the parsed components of a GPULlama3 model response.
     */
    public static class ParsedResponse {
        private final String thinkingContent;
        private final String actualResponse;

        public ParsedResponse(String thinkingContent, String actualResponse) {
            this.thinkingContent = thinkingContent;
            this.actualResponse = actualResponse;
        }

        /**
         * Returns the thinking content extracted from between &lt;think&gt; and &lt;/think&gt; tags.
         *
         * @return the thinking content, or null if no thinking content was found
         */
        public String getThinkingContent() {
            return thinkingContent;
        }

        /**
         * Returns the actual response content with thinking tags removed.
         *
         * @return the cleaned response content
         */
        public String getActualResponse() {
            return actualResponse;
        }

        /**
         * Returns true if the response contained thinking content.
         *
         * @return true if thinking content was found, false otherwise
         */
        public boolean hasThinking() {
            return thinkingContent != null && !thinkingContent.trim().isEmpty();
        }
    }

    /**
     * Parses a raw GPULlama3 response to separate thinking content from actual response.
     *
     * @param rawResponse the raw response from the model
     * @return ParsedResponse containing separated thinking and response content
     * @throws IllegalArgumentException if rawResponse is null
     */
    public static ParsedResponse parseResponse(String rawResponse) {
        if (rawResponse == null) {
            throw new IllegalArgumentException("Raw response cannot be null");
        }

        String thinking = null;
        String actualResponse = rawResponse;

        // Find <think> and </think> positions
        int thinkStart = rawResponse.indexOf("<think>");
        int thinkEnd = rawResponse.indexOf("</think>");

        if (thinkStart != -1 && thinkEnd != -1 && thinkEnd > thinkStart) {
            // Extract thinking content (skip the <think> tag itself)
            thinking = rawResponse.substring(thinkStart + 7, thinkEnd).trim();

            // Remove the entire thinking block from response
            String beforeThink = rawResponse.substring(0, thinkStart);
            String afterThink = rawResponse.substring(thinkEnd + 8); // Skip </think>
            actualResponse = (beforeThink + afterThink).trim();

            // Clean up any extra whitespace
            actualResponse = actualResponse.replaceAll("\\s+", " ").trim();
        }

        return new ParsedResponse(thinking, actualResponse);
    }

    /**
     * Convenience method to extract only the thinking content from a response.
     *
     * @param rawResponse the raw response from the model
     * @return the thinking content, or null if none found
     */
    public static String extractThinking(String rawResponse) {
        return parseResponse(rawResponse).getThinkingContent();
    }

    /**
     * Convenience method to extract only the cleaned response content.
     *
     * @param rawResponse the raw response from the model
     * @return the response content with thinking tags removed
     */
    public static String extractResponse(String rawResponse) {
        return parseResponse(rawResponse).getActualResponse();
    }
}
