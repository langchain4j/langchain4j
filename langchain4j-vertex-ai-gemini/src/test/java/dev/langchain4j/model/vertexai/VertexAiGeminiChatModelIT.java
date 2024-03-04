package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.vertexai.FunctionCallHelper.unwrapProtoValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void should_deny_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Be polite");
        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when-then
        assertThatThrownBy(() -> model.generate(systemMessage, userMessage))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("SystemMessage is currently not supported by Gemini");
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

        assertThat(response.finishReason()).isEqualTo(LENGTH);
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

    @Test
    void should_unwrap_proto_values() {
        // check basic values
        assertThat(unwrapProtoValue(Value.newBuilder().setStringValue("hello").build())).isEqualTo("hello");
        assertThat(unwrapProtoValue(Value.newBuilder().setBoolValue(false).build())).isEqualTo(false);
        assertThat(unwrapProtoValue(Value.newBuilder().setNumberValue(1.23).build())).isEqualTo(1.23);
        assertThat(unwrapProtoValue(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())).isEqualTo(null);

        // check list unwrapping
        ListValue listValue = ListValue.newBuilder()
            .addValues(Value.newBuilder().setStringValue("hello"))
            .addValues(Value.newBuilder().setBoolValue(true))
            .addValues(Value.newBuilder().setNumberValue(3.14))
            .build();
        assertThat(unwrapProtoValue(Value.newBuilder().setListValue(listValue).build())).isEqualTo(
            Arrays.asList("hello", true, 3.14)
        );

        // check struct unwrapping
        Struct struct = Struct.newBuilder()
            .putFields("name", Value.newBuilder().setStringValue("Guillaume").build())
            .putFields("numberOfKids", Value.newBuilder().setNumberValue(2).build())
            .putFields("kids", Value.newBuilder().setListValue(ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue("Marion").build())
                .addValues(Value.newBuilder().setStringValue("Érine").build())
                .build()).build())
            .putFields("flag", Value.newBuilder().setBoolValue(false).build())
            .build();
        HashMap<Object, Object> map = new HashMap<>();
        map.put("name", "Guillaume");
        map.put("numberOfKids", 2.0);
        map.put("kids", Arrays.asList("Marion", "Érine"));
        map.put("flag", false);
        assertThat(unwrapProtoValue(Value.newBuilder().setStructValue(struct).build())).isEqualTo(map);
    }

    @Test
    void should_convert_tool_specs() {
        // given
        ToolSpecification toolSpec = ToolSpecification.builder()
            .description("Give the weather forecast for a location")
            .name("getWeatherForecast")
            .addParameter("location", JsonSchemaProperty.STRING,
                JsonSchemaProperty.description("the location to get the weather forecast for"))
            .addOptionalParameter("days", JsonSchemaProperty.INTEGER,
                JsonSchemaProperty.description("the number of days in the forecast"))
            .build();

        // when
        Tool tool = FunctionCallHelper.convertToolSpecifications(Collections.singletonList(toolSpec));

        // then
        assertThat(tool.getFunctionDeclarationsCount()).isEqualTo(1);

        FunctionDeclaration funDecl = tool.getFunctionDeclarations(0);
        assertThat(funDecl.getDescription()).isEqualTo("Give the weather forecast for a location");
        assertThat(funDecl.getName()).isEqualTo("getWeatherForecast");

        Schema parameters = funDecl.getParameters();
        assertThat(parameters.getPropertiesCount()).isEqualTo(2);
        assertThat(parameters.getPropertiesMap().get("location").getType()).isEqualTo(Type.STRING);
        assertThat(parameters.getPropertiesMap().get("location").getDescription())
            .isEqualTo("the location to get the weather forecast for");
        assertThat(parameters.getRequiredCount()).isEqualTo(1);
        assertThat(parameters.getRequired(0)).isEqualTo("location");
        assertThat(parameters.getPropertiesMap().get("days").getType()).isEqualTo(Type.INTEGER);
    }

    @Test
    void should_convert_function_calls_to_tool_execution_requests_and_back() {
        // given
        FunctionCall functionCall = FunctionCall.newBuilder()
            .setName("getWeatherForecast")
            .setArgs(
                Struct.newBuilder()
                    .putFields("location", Value.newBuilder().setStringValue("Paris").build())
                    .build()
            )
            .build();

        // when
        ToolExecutionRequest toolExecutionRequest = FunctionCallHelper.fromFunctionCall(functionCall);
        FunctionCall sameFunctionCall = FunctionCallHelper.fromToolExecutionRequest(toolExecutionRequest);

        // then
        assertThat(functionCall).isEqualTo(sameFunctionCall);

        // given
        ToolExecutionRequest newExecutionRequest = ToolExecutionRequest.builder()
            .name("getWeatherForecast")
            .arguments("{\"location\":\"Paris\"}")
            .build();

        // when
        FunctionCall newFunctionCall = FunctionCallHelper.fromToolExecutionRequest(newExecutionRequest);
        ToolExecutionRequest sameExecutionRequest = FunctionCallHelper.fromFunctionCall(newFunctionCall);

        // then
        assertThat(newExecutionRequest).isEqualTo(sameExecutionRequest);
    }
}