package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class MistralAiStreamingChatModelIT {

    StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .temperature(0.1)
            .logResponses(true)
            .logRequests(true)
            .build();

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);

        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_length() {

        // given
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .maxTokens(10)
                .build();

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    //https://docs.mistral.ai/platform/guardrailing/
    @Test
    void should_stream_answer_and_system_prompt_to_enforce_guardrails() {

        // given
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .safePrompt(true)
                .build();

        // given
        UserMessage userMessage = userMessage("Hello, my name is Carlos");

        // then
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("respect");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(50);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop_with_multiple_messages() {

        // given
        UserMessage userMessage1 = userMessage("What is the capital of Peru?");
        UserMessage userMessage2 = userMessage("What is the capital of France?");
        UserMessage userMessage3 = userMessage("What is the capital of Canada?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(asList(userMessage1, userMessage2, userMessage3), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("lima");
        assertThat(response.content().text()).containsIgnoringCase("paris");
        assertThat(response.content().text()).containsIgnoringCase("ottawa");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(11 + 11 + 11);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_in_french_using_model_small_and_return_token_usage_and_finish_reason_stop() {

        // given - Mistral Small = Mistral-8X7B
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MISTRAL_SMALL.toString())
                .temperature(0.1)
                .build();

        UserMessage userMessage = userMessage("Quelle est la capitale du Pérou?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(18);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_in_spanish_using_model_small_and_return_token_usage_and_finish_reason_stop() {

        // given - Mistral Small = Mistral-8X7B
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MISTRAL_SMALL.toString())
                .temperature(0.1)
                .build();

        UserMessage userMessage = userMessage("¿Cuál es la capital de Perú?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(19);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_using_model_medium_and_return_token_usage_and_finish_reason_length() {

        // given - Mistral Medium = currently relies on an internal prototype model.
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MISTRAL_MEDIUM.toString())
                .maxTokens(10)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
