package dev.langchain4j.model.openai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.reactive.streaming.AbstractChatModelNonBlockingIT;

/**
 * OpenAI Responses-API binding of the shared non-blocking chat-model TCK
 * ({@link AbstractChatModelNonBlockingIT}). The Responses API has its own client and its own event vocabulary, so
 * its pipeline is policed separately from the Chat Completions one covered by {@link OpenAiChatModelNonBlockingIT};
 * both stream over the JDK {@code HttpClient} transport ({@code HttpClient-*} workers).
 */
class OpenAiResponsesChatModelNonBlockingIT extends AbstractChatModelNonBlockingIT {

    @Override
    protected ChatModel syncModel(String baseUrl, boolean logging) {
        return OpenAiResponsesChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("gpt-5-mini")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    @Override
    protected StreamingChatModel streamingModel(String baseUrl, boolean logging) {
        return OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("gpt-5-mini")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    @Override
    protected String nonStreamingResponseBody() {
        return "{\"id\":\"resp_1\",\"model\":\"gpt-5-mini\",\"status\":\"completed\",\"output\":["
                + "{\"type\":\"message\",\"id\":\"msg_1\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"output_text\",\"text\":\"Berlin\"}]}],"
                + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2}}";
    }

    @Override
    protected String streamingResponseBody() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("data: {\"type\":\"response.output_text.delta\",\"delta\":\"chunk-")
                    .append(i)
                    .append("\"}\n\n");
        }
        sb.append("data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",")
                .append("\"model\":\"gpt-5-mini\",\"status\":\"completed\",\"output\":[]}}\n\n");
        return sb.toString();
    }
}
