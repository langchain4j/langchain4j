package dev.langchain4j.model.gpullama3;

import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * Utility class for parsing GPULlama3 model responses that contain thinking content.
 * <p>
 * GPULlama3 models can generate responses with embedded thinking content in the format:
 * &lt;think&gt;thinking content...&lt;/think&gt;actual response content
 * <p>
 * This parser separates the thinking content from the actual response content for both
 * complete responses and streaming responses, while preserving the thinking tags.
 */
public class GPULlama3ResponseParser {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private GPULlama3ResponseParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Represents the parsed components of a GPULlama3 model response.
     */
    public static class ParsedResponse {
        private final String thinkingContent;
        private final String actualResponse;

        /**
         * Creates a new ParsedResponse.
         *
         * @param thinkingContent the thinking content including tags, or null if none
         * @param actualResponse the cleaned response content
         */
        public ParsedResponse(String thinkingContent, String actualResponse) {
            this.thinkingContent = thinkingContent;
            this.actualResponse = actualResponse;
        }

        /**
         * Returns the thinking content including &lt;think&gt; and &lt;/think&gt; tags.
         *
         * @return the thinking content with tags, or null if no thinking content was found
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
     * Preserves the thinking tags in the thinking content.
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
            // Extract thinking content INCLUDING the tags
            thinking = rawResponse.substring(thinkStart, thinkEnd + 8).trim(); // Include </think>

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
     * Returns thinking content with tags preserved.
     *
     * @param rawResponse the raw response from the model
     * @return the thinking content with tags, or null if none found
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

    /**
     * Creates a new streaming parser for real-time thinking content separation.
     *
     * @param handler the streaming response handler
     * @param model the GPULlama3 model instance for token decoding
     * @return a new StreamingThinkingParser instance
     */
    public static StreamingParser createStreamingParser(
            StreamingChatResponseHandler handler, org.beehive.gpullama3.model.Model model) {
        return new StreamingParser(handler, model);
    }

    /**
     * Parser for handling streaming responses with real-time thinking content separation.
     * <p>
     * This parser detects thinking content as tokens are generated and routes it to
     * the appropriate handler methods (onPartialThinking vs onPartialResponse).
     * The thinking tags are preserved and streamed as part of the thinking content.
     */
    public static class StreamingParser {
        private final StreamingChatResponseHandler handler;
        private final org.beehive.gpullama3.model.Model model;
        private final StringBuilder buffer = new StringBuilder();
        private boolean insideThinking = false;
        private int lastProcessedLength = 0;

        /**
         * Creates a new streaming parser.
         *
         * @param handler the streaming response handler
         * @param model the GPULlama3 model instance for token decoding
         */
        public StreamingParser(StreamingChatResponseHandler handler, org.beehive.gpullama3.model.Model model) {
            this.handler = handler;
            this.model = model;
        }

        /**
         * Processes each token as it's generated by the model.
         *
         * @param tokenId the token ID generated by the model
         */
        public void onToken(int tokenId) {
            // Check if this is a stop token and skip it
            if (model.chatFormat().getStopTokens().contains(tokenId)) {
                return; // Don't stream stop tokens like <|im_end|>
            }

            // Decode the token and add to buffer
            String tokenStr = model.tokenizer().decode(java.util.List.of(tokenId));
            buffer.append(tokenStr);

            String currentText = buffer.toString();

            // Process any new content since last time
            processNewContent(currentText);
        }

        /**
         * Processes new content in the buffer, detecting thinking state transitions
         * and routing content to appropriate handler methods.
         */
        private void processNewContent(String currentText) {
            if (currentText.length() <= lastProcessedLength) {
                return; // No new content
            }

            String newContent = currentText.substring(lastProcessedLength);

            // Process each character in the new content
            for (int i = 0; i < newContent.length(); i++) {
                int currentPosition = lastProcessedLength + i;

                // Check if we're starting thinking
                if (!insideThinking && isStartOfThinkTag(currentText, currentPosition)) {
                    insideThinking = true;
                    // Stream the opening tag as thinking
                    handler.onPartialThinking(new PartialThinking("<think>"));
                    i += 6; // Skip the rest of "<think>"
                    continue;
                }

                // Check if we're ending thinking
                if (insideThinking && isStartOfEndThinkTag(currentText, currentPosition)) {
                    // Stream the closing tag as thinking
                    handler.onPartialThinking(new PartialThinking("</think>"));
                    insideThinking = false;
                    i += 7; // Skip the rest of "</think>"
                    continue;
                }

                // Stream the character to appropriate handler
                char c = newContent.charAt(i);
                if (insideThinking) {
                    handler.onPartialThinking(new PartialThinking(String.valueOf(c)));
                } else {
                    handler.onPartialResponse(String.valueOf(c));
                }
            }

            lastProcessedLength = currentText.length();
        }

        /**
         * Checks if the text at the given position starts with "&lt;think&gt;".
         */
        private boolean isStartOfThinkTag(String text, int position) {
            return position + 7 <= text.length() && text.regionMatches(position, "<think>", 0, 7);
        }

        /**
         * Checks if the text at the given position starts with "&lt;/think&gt;".
         */
        private boolean isStartOfEndThinkTag(String text, int position) {
            return position + 8 <= text.length() && text.regionMatches(position, "</think>", 0, 8);
        }
    }
}
