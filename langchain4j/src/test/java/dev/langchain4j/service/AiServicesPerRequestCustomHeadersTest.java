package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.CompletableFuture;

class AiServicesPerRequestCustomHeadersTest {

    interface Assistant {

        String chat(String userMessage);
    }

    static class Clock {

        @Tool
        String currentTime() {
            return "12:00";
        }
    }

    @Test
    void chat_request_transformer_should_add_custom_headers_to_every_request_including_after_tool_calls() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("currentTime")
                        .arguments("{}")
                        .build()),
                AiMessage.from("It is 12:00"));

        AtomicInteger callCounter = new AtomicInteger();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new Clock())
                .chatRequestTransformer(chatRequest -> chatRequest.toBuilder()
                        .customHeaders(Map.of("X-Correlation-Id", "call-" + callCounter.incrementAndGet()))
                        .build())
                .build();

        // when
        String answer = assistant.chat("What time is it?");

        // then
        assertThat(answer).isEqualTo("It is 12:00");
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(customHeadersOf(chatModel.requests().get(0))).containsOnly(entry("X-Correlation-Id", "call-1"));
        assertThat(customHeadersOf(chatModel.requests().get(1))).containsOnly(entry("X-Correlation-Id", "call-2"));
        assertThat(chatModel.requests().get(1).toolSpecifications()).isNotEmpty();
    }

    private static Map<String, String> customHeadersOf(ChatRequest chatRequest) {
        return chatRequest.customHeaders();
    }

    interface AssistantWithParameters {

        String chat(@UserMessage String userMessage, ChatRequestParameters parameters);
    }

    interface StreamingAssistantWithParameters {

        TokenStream chat(@UserMessage String userMessage, ChatRequestParameters parameters);
    }

    private static final ChatRequestParameters PARAMETERS_WITH_HEADERS = ChatRequestParameters.builder()
            .httpParameters(HttpParameters.builder()
                    .customHeaders(Map.of("X-Correlation-Id", "123"))
                    .build())
            .build();

    private static final AiMessage TOOL_CALL = AiMessage.from(ToolExecutionRequest.builder()
            .id("1")
            .name("currentTime")
            .arguments("{}")
            .build());

    @Test
    void custom_headers_from_method_argument_should_reach_every_request_including_after_tool_calls() {

        // given
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(TOOL_CALL, AiMessage.from("It is 12:00"));

        AssistantWithParameters assistant = AiServices.builder(AssistantWithParameters.class)
                .chatModel(chatModel)
                .tools(new Clock())
                .build();

        // when
        String answer = assistant.chat("What time is it?", PARAMETERS_WITH_HEADERS);

        // then
        assertThat(answer).isEqualTo("It is 12:00");
        assertThat(chatModel.requests()).hasSize(2);
        chatModel.requests().forEach(request -> assertThat(customHeadersOf(request))
                .containsOnly(entry("X-Correlation-Id", "123")));
    }

    @Test
    void custom_headers_from_method_argument_should_reach_every_streaming_request_including_after_tool_calls()
            throws Exception {

        // given
        StreamingChatModelMock streamingChatModel =
                StreamingChatModelMock.thatAlwaysStreams(TOOL_CALL, AiMessage.from("It is 12:00"));

        StreamingAssistantWithParameters assistant = AiServices.builder(StreamingAssistantWithParameters.class)
                .streamingChatModel(streamingChatModel)
                .tools(new Clock())
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat("What time is it?", PARAMETERS_WITH_HEADERS)
                .onPartialResponse(partialResponse -> {})
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();

        // then
        assertThat(futureResponse.get(5, SECONDS).aiMessage().text()).isEqualTo("It is 12:00");
        assertThat(streamingChatModel.requests()).hasSize(2);
        streamingChatModel.requests().forEach(request -> assertThat(customHeadersOf(request))
                .containsOnly(entry("X-Correlation-Id", "123")));
    }
}

