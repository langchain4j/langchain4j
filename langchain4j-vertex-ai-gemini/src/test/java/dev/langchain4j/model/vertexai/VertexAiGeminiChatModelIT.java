package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VertexAiGeminiChatModelIT {

    static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    ChatLanguageModel model = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName("gemini-pro")
            .build();

    ChatLanguageModel visionModel = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName("gemini-pro-vision")
            .build();

    @Test
    void should_generate_response() {

        // given
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @ParameterizedTest
    @MethodSource
    void should_merge_system_messages_into_user_message(List<ChatMessage> messages) {

        // when
        Response<AiMessage> response = model.generate(messages);

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
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-pro")
                .maxOutputTokens(1)
                .build();

        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(4);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel("gemini-pro", vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        ChatLanguageModel model = new VertexAiGeminiChatModel(generativeModel, generationConfig);

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

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
        Response<AiMessage> response = visionModel.generate(userMessage);

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
        Response<AiMessage> response = visionModel.generate(userMessage);

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
        Response<AiMessage> response = visionModel.generate(userMessage);

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
        Response<AiMessage> response = visionModel.generate(userMessage);

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
        Response<AiMessage> response = visionModel.generate(userMessage);

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
        Response<AiMessage> response = visionModel.generate(userMessage);

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
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dog")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_tools_for_function_calling() {

        // given
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
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
        Response<AiMessage> messageResponse = model.generate(allMessages, weatherToolSpec);

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

        Response<AiMessage> weatherResponse = model.generate(allMessages);

        // then
        System.out.println("Answer: " + weatherResponse.content().text());
        assertThat(weatherResponse.content().text()).containsIgnoringCase("sunny");
    }

    static class Calculator {

        @Tool("Adds two given numbers")
        double add(double a, double b) {
            System.out.printf("Called add(%s, %s)%n", a, b);
            return a + b;
        }

        @Tool("Multiplies two given numbers")
        String multiply(double a, double b) {
            System.out.printf("Called multiply(%s, %s)%n", a, b);
            return String.valueOf(a * b);
        }
    }

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_use_tools_with_AiService() {

        // given
        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(calculator)
                .build();

        // when
        String answer = assistant.chat("How much is 74589613588 + 4786521789?");

        // then
        // assertThat(answer).contains("79376135377"); TODO

        verify(calculator).add(74589613588.0, 4786521789.0);
        verifyNoMoreInteractions(calculator);
    }

    @Test
    void should_use_tools_with_AiService_2() {

        // given
        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(calculator)
                .build();

        // when
        String answer = assistant.chat("How much is 257 * 467?");

        // then
        // assertThat(answer).contains("120019"); TODO

        verify(calculator).multiply(257, 467);
        verifyNoMoreInteractions(calculator);
    }
}