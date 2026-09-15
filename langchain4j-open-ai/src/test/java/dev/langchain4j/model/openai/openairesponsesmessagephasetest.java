package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Reasoning models may return several {@code message} items in a single turn, each tagged with a
 * {@code phase} ({@code commentary} followed by {@code final_answer}). Only the final answer belongs
 * in {@link ChatResponse#aiMessage()}, no matter whether the response was streamed or fetched in one
 * piece.
 */
class OpenAiResponsesMessagePhaseTest {

    private static final String COMMENTARY_TEXT =
            "Just to confirm, you'd like to make a payment of $40 on August 10. Is that correct?";

    private static ServerSentEvent event(String json) {
        return new ServerSentEvent(null, json);
    }

    private static String completedEvent(String output) {
        return "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"completed\",\"output\":"
                + output + "}}";
    }

    /** The same payload the {@code response.completed} event carries, as a non-streamed body. */
    private static String responseBody(String output) {
        return "{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"completed\",\"output\":" + output + "}";
    }

    private static String messageItem(String phase, String text) {
        String phaseField = phase == null ? "" : ", \"phase\":\"" + phase + "\"";
        return "{\"type\":\"message\"" + phaseField
                + ", \"role\":\"assistant\", \"status\":\"completed\", \"content\":[{\"type\":\"output_text\", \"text\":\""
                + text + "\"}]}";
    }

    private ChatResponse streamChatWith(ServerSentEvent... events) throws Exception {
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(List.of(events));

        OpenAiResponsesStreamingChatModel.builder()
                .apiKey("dummy")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.4-mini")
                .build()
                .chat("Hello", new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {}

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        futureResponse.complete(completeResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        futureResponse.completeExceptionally(error);
                    }
                });

        return futureResponse.get(5, TimeUnit.SECONDS);
    }

    private ChatResponse chatWith(String responseBody) throws Exception {
        SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(responseBody)
                .build();

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

        return OpenAiResponsesChatModel.builder()
                .apiKey("dummy")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.4-mini")
                .build()
                .chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }

    @Test
    void should_not_duplicate_the_reply_when_commentary_and_final_answer_carry_the_same_text() throws Exception {
        ChatResponse response = streamChatWith(event(completedEvent("["
                + messageItem("commentary", COMMENTARY_TEXT) + ","
                + messageItem("final_answer", COMMENTARY_TEXT) + "]")));

        assertThat(response.aiMessage().text()).isEqualTo(COMMENTARY_TEXT);
    }

    @Test
    void should_drop_the_commentary_preamble_when_the_final_answer_differs() throws Exception {
        ChatResponse response = streamChatWith(event(completedEvent("["
                + messageItem("commentary", "I'll confirm the payment details for you.") + ","
                + messageItem("final_answer", "Your payment of $40 is scheduled for August 10.") + "]")));

        assertThat(response.aiMessage().text()).isEqualTo("Your payment of $40 is scheduled for August 10.");
    }

    @Test
    void should_fall_back_to_every_message_item_when_no_item_carries_a_phase() throws Exception {
        ChatResponse response = streamChatWith(
                event(completedEvent("[" + messageItem(null, "Hello") + "," + messageItem(null, ", world") + "]")));

        assertThat(response.aiMessage().text()).isEqualTo("Hello, world");
    }

    @Test
    void should_fall_back_to_every_message_item_when_no_final_answer_phase_is_present() throws Exception {
        ChatResponse response = streamChatWith(event(completedEvent("["
                + messageItem("commentary", "Let me check that.") + ","
                + messageItem("commentary", " One moment.") + "]")));

        assertThat(response.aiMessage().text()).isEqualTo("Let me check that. One moment.");
    }

    @Test
    void should_not_duplicate_the_reply_when_the_response_is_not_streamed() throws Exception {
        ChatResponse response = chatWith(responseBody("["
                + messageItem("commentary", COMMENTARY_TEXT) + ","
                + messageItem("final_answer", COMMENTARY_TEXT) + "]"));

        assertThat(response.aiMessage().text()).isEqualTo(COMMENTARY_TEXT);
    }

    @Test
    void should_keep_the_previous_behaviour_when_the_response_has_no_phase() throws Exception {
        ChatResponse response =
                chatWith(responseBody("[" + messageItem(null, "Hello") + "," + messageItem(null, ", world") + "]"));

        assertThat(response.aiMessage().text()).isEqualTo("Hello, world");
    }

    @Test
    void should_surface_the_final_answer_when_only_some_items_carry_a_phase() throws Exception {
        ChatResponse response = chatWith(responseBody(
                "[" + messageItem("commentary", "Preamble.") + "," + messageItem("final_answer", "The answer.") + "]"));

        assertThat(response.aiMessage().text()).isEqualTo("The answer.");
    }

    @Test
    void should_ignore_text_that_is_not_part_of_a_message_item() throws Exception {
        List<ServerSentEvent> events = new ArrayList<>();
        events.add(event(completedEvent("["
                + "{\"type\":\"reasoning\", \"summary\":[{\"type\":\"summary_text\", \"text\":\"Thinking.\"}]},"
                + messageItem("final_answer", "Only this.") + "]")));

        ChatResponse response = streamChatWith(events.toArray(new ServerSentEvent[0]));

        assertThat(response.aiMessage().text()).isEqualTo("Only this.");
    }
}
