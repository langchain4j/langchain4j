package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.vertexai.VertexAiGeminiChatModelIT.CAT_IMAGE_URL;
import static dev.langchain4j.model.vertexai.VertexAiGeminiChatModelIT.DICE_IMAGE_URL;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class VertexAiGeminiStreamingChatModelIT {

    StreamingChatLanguageModel model = VertexAiGeminiStreamingChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName("gemini-pro")
            .build();

    StreamingChatLanguageModel visionModel = VertexAiGeminiStreamingChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName("gemini-pro-vision")
            .build();

    @Test
    void should_stream_answer() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).contains("Berlin");

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource
    void should_merge_system_messages_into_user_message(List<ChatMessage> messages) {

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(messages, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("liebe");
    }

    static Stream<Arguments> should_merge_system_messages_into_user_message() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from("I love you")
                        )
                ))
                .add(Arguments.of(
                        asList(
                                UserMessage.from("I love you"),
                                SystemMessage.from("Translate in German")
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in Italian"),
                                UserMessage.from("I love you"),
                                SystemMessage.from("No, translate in German!")
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from(asList(
                                        TextContent.from("I love you"),
                                        TextContent.from("I see you")
                                ))
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from(asList(
                                        TextContent.from("I see you"),
                                        TextContent.from("I love you")
                                ))
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from("I see you"),
                                AiMessage.from("Ich sehe dich"),
                                UserMessage.from("I love you")
                        )
                ))
                .build();
    }

    @Test
    void should_respect_maxOutputTokens() {

        // given
        StreamingChatLanguageModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-pro")
                .maxOutputTokens(1)
                .build();

        String userMessage = "Tell me a joke";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).isNotBlank();

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(4);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(1);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel("gemini-pro", vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        StreamingChatLanguageModel model = new VertexAiGeminiStreamingChatModel(generativeModel, generationConfig);

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).contains("Berlin");
    }

    @Test
    void should_accept_text_and_image_from_public_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image_from_google_storage_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images_from_public_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_google_storage_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                ImageContent.from("gs://langchain4j-test/dice.png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_base64_images() {

        // given
        String catBase64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        String diceBase64Data = Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(catBase64Data, "image/png"),
                ImageContent.from(diceBase64Data, "image/png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from("gs://langchain4j-test/dog.jpg"),
                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dog")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_function_call() {

        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-pro")
                .build();

        ToolSpecification weatherToolSpec = ToolSpecification.builder()
                .name("getWeatherForecast")
                .description("Get the weather forecast for a location")
                .addParameter("location", JsonSchemaProperty.STRING,
                        JsonSchemaProperty.description("the location to get the weather forecast for"))
                .build();

        List<ChatMessage> allMessages = new ArrayList<>();

        UserMessage weatherQuestion = UserMessage.from("What is the weather in Paris?");
        System.out.println("Question: " + weatherQuestion.text());
        allMessages.add(weatherQuestion);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(allMessages, weatherToolSpec, handler);
        Response<AiMessage> messageResponse = handler.get();

        // then
        assertThat(messageResponse.content().hasToolExecutionRequests()).isTrue();
        ToolExecutionRequest toolExecutionRequest = messageResponse.content().toolExecutionRequests().get(0);

        assertThat(toolExecutionRequest.arguments()).contains("Paris");
        assertThat(toolExecutionRequest.name()).isEqualTo("getWeatherForecast");

        allMessages.add(messageResponse.content());

        // when (feeding the function return value back)
        ToolExecutionResultMessage toolExecResMsg = ToolExecutionResultMessage.from(toolExecutionRequest,
                "{\"location\":\"Paris\",\"forecast\":\"sunny\", \"temperature\": 20}");
        allMessages.add(toolExecResMsg);

        handler = new TestStreamingResponseHandler<>();
        model.generate(allMessages, handler);
        Response<AiMessage> weatherResponse = handler.get();

        // then
        System.out.println("Answer: " + weatherResponse.content().text());
        assertThat(weatherResponse.content().text()).containsIgnoringCase("sunny");
    }
}