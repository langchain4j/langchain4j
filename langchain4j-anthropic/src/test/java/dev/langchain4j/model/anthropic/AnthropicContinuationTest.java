package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.internal.client.Json.fromJson;
import static dev.langchain4j.model.anthropic.internal.client.Json.toJson;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.client.AnthropicCreateMessageOptions;
import dev.langchain4j.model.anthropic.internal.client.DefaultAnthropicClient;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AnthropicContinuationTest {
    private static final String BLOCKS = """
            [{"type":"thinking","thinking":"","signature":"signature-one"},
             {"type":"text","text":"First text"},
             {"type":"redacted_thinking","data":"opaque"},
             {"type":"thinking","thinking":"Second thought","signature":"signature-two"},
             {"type":"tool_use","id":"call_1","name":"search","input":{"query":"x"}},
             {"type":"thinking","thinking":"","signature":"signature-three"},
             {"type":"text","text":"Last text"}]
            """;

    @Test
    void should_preserve_order_and_signatures_across_storage_and_replay() {
        AiMessage message = toAiMessage(List.of(fromJson(BLOCKS, AnthropicContent[].class)), true);
        assertThat(message.text()).isEqualTo("First text\nLast text");
        // Attribute values survive a generic JSON-backed chat-memory round trip.
        message = message.toBuilder()
                .attributes(fromJson(toJson(message.attributes()), Map.class))
                .build();
        assertThat(fromJson(toJson(toAnthropicMessages(List.of(message), true).get(0).content), List.class))
                .isEqualTo(fromJson(BLOCKS, List.class));
        assertThat(toAnthropicMessages(List.of(message), false).get(0).content)
                .noneMatch(c -> "thinking".equals(c.type) || "redacted_thinking".equals(c.type));
        AiMessage edited = message.withText("Edited answer");
        assertThat(toJson(toAnthropicMessages(List.of(edited), true))).contains("Edited answer");
        assertThat(toAiMessage(List.of(fromJson(BLOCKS, AnthropicContent[].class)), false)
                        .attributes())
                .doesNotContainKey(CONTINUATION_CONTENT_KEY);
    }

    @Test
    void should_preserve_final_answer_and_signature_only_turns() {
        for (String blocks : List.of(
                "[{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"final\"},{\"type\":\"text\",\"text\":\"Done\"}]",
                "[{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"empty\"}]")) {
            AiMessage message = toAiMessage(List.of(fromJson(blocks, AnthropicContent[].class)), true);
            assertThat(fromJson(
                            toJson(toAnthropicMessages(List.of(message), true).get(0).content), List.class))
                    .isEqualTo(fromJson(blocks, List.class));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_reassemble_streamed_blocks_without_joining_signatures() throws Exception {
        List<Map<String, Object>> blocks = fromJson(BLOCKS, List.class);
        List<ServerSentEvent> events = new ArrayList<>();
        events.add(new ServerSentEvent("message_start", "{\"message\":{\"id\":\"test\",\"model\":\"test\"}}"));
        for (int i = 0; i < blocks.size(); i++) {
            Map<String, Object> block = blocks.get(i);
            Map<String, Object> start = new LinkedHashMap<>(block);
            String type = (String) block.get("type");
            if (type.equals("thinking")) {
                start.put("thinking", "");
                start.remove("signature");
            }
            if (type.equals("text")) start.put("text", "");
            if (type.equals("tool_use")) start.put("input", Map.of());
            events.add(new ServerSentEvent("content_block_start", toJson(Map.of("index", i, "content_block", start))));
            if (type.equals("thinking")) {
                delta(events, i, "thinking", block.get("thinking"));
                String signature = (String) block.get("signature");
                delta(events, i, "signature", signature.substring(0, 4));
                delta(events, i, "signature", signature.substring(4));
            } else if (type.equals("text")) delta(events, i, "text", block.get("text"));
            else if (type.equals("tool_use")) delta(events, i, "partial_json", toJson(block.get("input")));
            events.add(new ServerSentEvent("content_block_stop", toJson(Map.of("index", i))));
        }
        events.add(new ServerSentEvent("message_stop", "{}"));
        var client = DefaultAnthropicClient.builder()
                .apiKey("test")
                .baseUrl("http://unused")
                .version("2023-06-01")
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(events)))
                .build();
        CompletableFuture<ChatResponse> result = new CompletableFuture<>();
        client.createMessage(
                AnthropicCreateMessageRequest.builder()
                        .model("test")
                        .maxTokens(100)
                        .build(),
                new AnthropicCreateMessageOptions(true),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String text) {}

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        result.complete(response);
                    }

                    @Override
                    public void onError(Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
        AiMessage message = result.get(5, TimeUnit.SECONDS).aiMessage();
        assertThat(fromJson(toJson(toAnthropicMessages(List.of(message), true).get(0).content), List.class))
                .isEqualTo(blocks);
    }

    private static void delta(List<ServerSentEvent> events, int index, String key, Object value) {
        events.add(new ServerSentEvent(
                "content_block_delta", toJson(Map.of("index", index, "delta", Map.of(key, value)))));
    }
}
