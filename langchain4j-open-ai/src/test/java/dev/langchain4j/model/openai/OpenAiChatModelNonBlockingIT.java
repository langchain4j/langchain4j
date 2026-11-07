package dev.langchain4j.model.openai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.reactive.streaming.AbstractChatModelNonBlockingIT;

/**
 * OpenAI binding of the shared non-blocking chat-model TCK ({@link AbstractChatModelNonBlockingIT}). OpenAI streams
 * over the JDK {@code HttpClient} transport, so responses are parsed and dispatched on its workers ({@code HttpClient-*}).
 */
class OpenAiChatModelNonBlockingIT extends AbstractChatModelNonBlockingIT {

    @Override
    protected ChatModel syncModel(String baseUrl, boolean logging) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    @Override
    protected StreamingChatModel streamingModel(String baseUrl, boolean logging) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    @Override
    protected String nonStreamingResponseBody() {
        return "{\"id\":\"x\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Berlin\"},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    @Override
    protected String streamingResponseBody() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("data: {\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\",")
                    .append("\"choices\":[{\"index\":0,\"delta\":{\"content\":\"chunk-")
                    .append(i)
                    .append("\"},\"finish_reason\":null}]}")
                    .append("\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }
}
