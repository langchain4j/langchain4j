package dev.langchain4j.model.bedrock;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.bedrock.converse.BedrockChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelWithConverseAPIIT {

    private static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    //    @BeforeEach
    //    void pauseBeforeTest() throws InterruptedException {
    //        Thread.sleep(60000); // AWS bedrock has a default 1 request per minute quota for claude 3.5 sonnet
    //    }

    @Test
    void should_generate_with_default_config() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");
        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void should_chat_with_default_config() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");

        assertThat(bedrockChatModel).isNotNull();

        ChatResponse response = bedrockChatModel.chat(ChatRequest.builder()
                .messages(List.of(UserMessage.from("hi, how are you doing?")))
                .responseFormat(ResponseFormat.TEXT)
                .build());

        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void should_chat_with_request_parameters_override() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");

        assertThat(bedrockChatModel).isNotNull();

        ChatResponse response = bedrockChatModel.chat(ChatRequest.builder()
                .messages(List.of(UserMessage.from("who are you and who creates you?")))
                .parameters(ChatRequestParameters.builder()
                        .modelName("us.amazon.nova-micro-v1:0")
                        .temperature(0.1d)
                        .build())
                .build());

        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.aiMessage().text()).containsAnyOf("Amazon", "AWS");
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void should_generate_with_default_config_and_system_message() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");

        assertThat(bedrockChatModel).isNotNull();

        SystemMessage systemMessage = SystemMessage.from("You are very helpful assistant. Answer client's question.");
        UserMessage userMessage = UserMessage.from("hi, how are you doing?");

        Response<AiMessage> response = bedrockChatModel.generate(systemMessage, userMessage);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void should_generate_with_default_config_and_image_content() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");

        assertThat(bedrockChatModel).isNotNull();

        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        Response<AiMessage> response = bedrockChatModel.generate(userMessage);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void should_call_function() {
        ChatLanguageModel model = BedrockChatModel.builder()
                .modelId("anthropic.claude-3-5-sonnet-20240620-v1:0")
                .build();

        UserMessage userMessage = userMessage(
                "Give three numbers, ordered by size: the sum of two plus two, the square of four, and finally the cube of eight.");

        List<ToolSpecification> toolSpecifications = asList(
                ToolSpecification.builder()
                        .name("sum")
                        .description("returns a sum of two numbers")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("first")
                                .addIntegerProperty("second")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("square")
                        .description("returns the square of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("cube")
                        .description("returns the cube of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .build())
                        .build());

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), toolSpecifications);

        AiMessage aiMessage = response.content();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage);
        messages.add(aiMessage);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(3);
        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            assertThat(toolExecutionRequest.name()).isNotEmpty();
            ToolExecutionResultMessage toolExecutionResultMessage;
            if (toolExecutionRequest.name().equals("sum")) {
                assertThat(toolExecutionRequest.arguments())
                        .isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "4");
            } else if (toolExecutionRequest.name().equals("square")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 4}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "16");
            } else if (toolExecutionRequest.name().equals("cube")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 8}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "512");
            } else {
                throw new AssertionError("Unexpected tool name: " + toolExecutionRequest.name());
            }
            messages.add(toolExecutionResultMessage);
        }

        Response<AiMessage> response2 = model.generate(messages, toolSpecifications);
        AiMessage aiMessage2 = response2.content();

        // then
        assertThat(aiMessage2.text()).contains("4", "16", "512");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isPositive();
        assertThat(tokenUsage2.outputTokenCount()).isPositive();
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    static class WeatherTools {
        enum TemperatureUnit {CELSIUS, FAHRENHEIT, KELVIN}

        @Tool("Returns the weather forecast for a given city")
        String getWeather(
                @P("The city for which the weather forecast should be returned") String city,
                TemperatureUnit temperatureUnit
        ) {
            return "12°C";
        }
    }

    interface Assistant  {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    void should_AiServices_chat_with_tool_enum_parameter() {
        //Given
        ChatLanguageModel model =
                BedrockChatModel.builder().modelId("us.amazon.nova-micro-v1:0").temperature(0.1f).build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(new WeatherTools())
                .build();

        //When
        String answer = assistant.chat("What will the weather be like in London tomorrow? Give the temperature in degrees Celsius.");

        //Then
        assertThat(answer).containsAnyOf("12°C", "12 degrees Celsius");
    }

    @Test
    void should_call_function_with_no_argument() {
        ChatLanguageModel model = BedrockChatModel.builder()
                .modelId("anthropic.claude-3-5-sonnet-20240620-v1:0")
                .build();

        UserMessage userMessage = userMessage("What time is it?");

        // This test will use the function called "getCurrentDateAndTime" which takes no arguments
        String toolName = "getCurrentDateAndTime";

        ToolSpecification noArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current date and time")
                .build();

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), noArgToolSpec);

        AiMessage aiMessage = response.content();

        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{}");
    }

    @Test
    void should_accept_PDF_documents() {
        // given
        ChatLanguageModel model =
                BedrockChatModel.builder().modelId("us.amazon.nova-lite-v1:0").build();
        UserMessage msg = UserMessage.from(
                PdfFileContent.from(
                        Paths.get("src/test/resources/gemini-doc-snapshot.pdf").toUri()),
                TextContent.from("Provide a summary of the document"));

        // when
        Response<AiMessage> response = model.generate(singletonList(msg));

        // then
        assertThat(response.content().text()).containsIgnoringCase("Gemini");
    }
}
