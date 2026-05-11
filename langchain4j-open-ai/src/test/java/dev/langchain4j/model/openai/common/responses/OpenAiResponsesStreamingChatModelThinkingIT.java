package dev.langchain4j.model.openai.common.responses;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

import java.util.List;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesStreamingChatModelThinkingIT {

    private static final String ENCRYPTED_REASONING_KEY = "encrypted_reasoning";

    private static final ToolSpecification WEATHER_TOOL = ToolSpecification.builder()
            .name("getWeather")
            .description("Returns the current weather for a given city")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city")
                    .required("city")
                    .build())
            .build();

    private static final ToolSpecification TIME_TOOL = ToolSpecification.builder()
            .name("getTime")
            .description("Returns the current time for a given country")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("country")
                    .required("country")
                    .build())
            .build();

    @Test
    void should_return_reasoning_summary() {

        // given
        String reasoningSummary = "auto";

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
                .reasoningSummary(reasoningSummary)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(of(userMessage), spyHandler);

        // then
        ChatResponse chatResponse = spyHandler.get();
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.thinking()).isEqualTo(spyHandler.getThinking());

        OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) chatResponse.tokenUsage();
        assertThat(tokenUsage.outputTokensDetails().reasoningTokens()).isNotNull();

        InOrder inOrder = inOrder(spyHandler);
        inOrder.verify(spyHandler).get();
        inOrder.verify(spyHandler, atLeastOnce()).onPartialThinking(any(), any());
        inOrder.verify(spyHandler, atLeastOnce()).onPartialResponse(any(), any());
        inOrder.verify(spyHandler).onCompleteResponse(any());
        inOrder.verify(spyHandler).getThinking();
        inOrder.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler);
    }

    @Test
    void should_not_return_reasoning_summary_when_not_requested() {

        // given
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(of(userMessage), spyHandler);

        // then
        ChatResponse chatResponse = spyHandler.get();
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();

        verify(spyHandler, never()).onPartialThinking(any());
        verify(spyHandler, never()).onPartialThinking(any(), any());
    }

    @Test
    void should_return_encrypted_reasoning_and_send_it_back__single_tool_call() {

        // given
        List<String> include = List.of("reasoning.encrypted_content");

        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
                .include(include)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .build())
                .build();

        // when
        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler1);

        // then
        AiMessage aiMessage1 = handler1.get().aiMessage();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage1.toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        String encryptedContent1 = aiMessage1.attribute(ENCRYPTED_REASONING_KEY, String.class);
        assertThat(encryptedContent1).isNotBlank();

        // when
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        userMessage,
                        aiMessage1,
                        ToolExecutionResultMessage.from(aiMessage1.toolExecutionRequests().get(0), "sunny, 22°C"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .build())
                .build();

        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model.chat(chatRequest2, handler2);

        // then
        AiMessage aiMessage2 = handler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // verify encrypted_content was sent back in turn 2
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains("encrypted_content")
                .contains(encryptedContent1);
    }

    @Test
    void should_return_encrypted_reasoning_and_send_it_back__two_parallel_tool_calls() {

        // given
        List<String> include = List.of("reasoning.encrypted_content");

        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
                .include(include)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from(
                "What is the weather in Munich and the current time in France? Call both tools in parallel.");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL, TIME_TOOL)
                        .build())
                .build();

        // when
        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler1);

        // then
        AiMessage aiMessage1 = handler1.get().aiMessage();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(2);
        String encryptedContent1 = aiMessage1.attribute(ENCRYPTED_REASONING_KEY, String.class);
        assertThat(encryptedContent1).isNotBlank();

        // when
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        userMessage,
                        aiMessage1,
                        ToolExecutionResultMessage.from(aiMessage1.toolExecutionRequests().get(0), "sunny, 22°C"),
                        ToolExecutionResultMessage.from(aiMessage1.toolExecutionRequests().get(1), "14:35"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL, TIME_TOOL)
                        .build())
                .build();

        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model.chat(chatRequest2, handler2);

        // then
        AiMessage aiMessage2 = handler2.get().aiMessage();

        // verify encrypted_content was sent back in turn 2
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains("encrypted_content")
                .contains(encryptedContent1);
    }
}
