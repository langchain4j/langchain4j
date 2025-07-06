package dev.langchain4j.model.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.openai.OpenAiChatModelName.O3_MINI;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelIT {

    static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .parameters(JsonObjectSchema.builder()
                    .addIntegerProperty("first")
                    .addIntegerProperty("second")
                    .required("first", "second")
                    .build())
            .build();

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @EnumSource(
            value = OpenAiChatModelName.class,
            mode = EXCLUDE,
            names = {
                    "GPT_4_32K", // don't have access
                    "GPT_4_32K_0613", // don't have access
                    "O3", // don't have access
                    "O3_2025_04_16", // don't have access
                    "O1_MINI", // does not support 'system' role with this model
                    "O1_MINI_2024_09_12", // does not support 'system' role with this model
                    "O1_PREVIEW", // does not support 'system' role with this model
                    "O1_PREVIEW_2024_09_12", // does not support 'system' role with this model
            })
    void should_support_all_model_names(OpenAiChatModelName modelName) {

        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from("Be concise"), UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text()).containsIgnoringCase("Berlin");
    }

    @Test
    void should_respect_maxTokens() {

        // given
        int maxTokens = 1;

        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxTokens(maxTokens)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Tell me a long story");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxTokens);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_respect_maxCompletionTokens() {

        // given
        int maxCompletionTokens = 1;

        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxCompletionTokens(maxCompletionTokens)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Tell me a long story");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxCompletionTokens);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        ChatResponse secondResponse = model.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isPositive();
        assertThat(secondTokenUsage.outputTokenCount()).isPositive();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_tool_forcefully_then_answer() {

        // given
        UserMessage userMessage = userMessage("I have 2 apples and 2 pears");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(calculator)
                        .toolChoice(REQUIRED)
                        .build())
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        ChatResponse secondResponse = model.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isPositive();
        assertThat(secondTokenUsage.outputTokenCount()).isPositive();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        // given
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("2+2=? 3+3=?");
        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 = from(toolExecutionRequest2, "6");

        List<ChatMessage> messages =
                asList(userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when
        ChatResponse secondResponse = model.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isPositive();
        assertThat(secondTokenUsage.outputTokenCount()).isPositive();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_valid_json() throws JsonProcessingException {

        // given
        @JsonIgnoreProperties(ignoreUnknown = true) // to ignore the "joke" field
        record Person(String name, String surname) {
        }

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. "
                + "Before returning, tell me a joke."; // nudging it to say something additionally to json

        String responseFormat = "json_object";

        ChatModel modelGeneratingJson = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .responseFormat(responseFormat)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        String json = modelGeneratingJson.chat(userMessage);

        // then
        Person person = new ObjectMapper().readValue(json, Person.class);
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.surname).isEqualTo("Heisler");
    }

    @Test
    void should_accept_image_url() {

        // given
        ImageContent imageContent = ImageContent.from(CAT_IMAGE_URL);
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image() {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply in one word."), ImageContent.from(CAT_IMAGE_URL));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images() {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply with one word per image."),
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
                TextContent.from("What do you see? Reply with one word per image."));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_audio_content() throws Exception {

        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-4o-audio-preview")
                .temperature(0.0)
                .logRequests(false) // avoid base64 output
                .logResponses(true)
                .build();

        Path file =
                Paths.get(getClass().getClassLoader().getResource("sample.wav").toURI());
        String audioBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));

        UserMessage userMessage = UserMessage.from(
                TextContent.from("What is on the audio?"), AudioContent.from(audioBase64, "audio/wav"));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("hello");
    }

    @Test
    void should_answer_with_reasoning_effort() {

        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(O3_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        ChatRequest chatRequestWithLowReasoningEffort = ChatRequest.builder()
                .messages(userMessage)
                .parameters(OpenAiChatRequestParameters.builder()
                        .reasoningEffort("low")
                        .build())
                .build();

        ChatRequest chatRequestWithMediumReasoningEffort = ChatRequest.builder()
                .messages(userMessage)
                .parameters(OpenAiChatRequestParameters.builder()
                        .reasoningEffort("high")
                        .build())
                .build();

        // when
        ChatResponse chatResponseWithLowReasoningEffort = model.chat(chatRequestWithLowReasoningEffort);
        ChatResponse chatResponseWithMediumReasoningEffort = model.chat(chatRequestWithMediumReasoningEffort);

        // then
        Integer lowReasoningTokens = ((OpenAiTokenUsage) chatResponseWithLowReasoningEffort.tokenUsage())
                .outputTokensDetails()
                .reasoningTokens();
        Integer mediumReasoningTokens = ((OpenAiTokenUsage) chatResponseWithMediumReasoningEffort.tokenUsage())
                .outputTokensDetails()
                .reasoningTokens();
        assertThat(lowReasoningTokens).isLessThan(mediumReasoningTokens);
    }

    @Test
    void should_accept_pdf_file_content() throws Exception {

        // given
        Path file = Paths.get(getClass().getClassLoader().getResource("sample.pdf").toURI());
        String pdfBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        PdfFile pdfFile = PdfFile.builder()
                .base64Data(pdfBase64)
                .mimeType("application/pdf")
                .build();

        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("What information is in the attached PDF? Return only the exacted text."))
                .addContent(PdfFileContent.from(pdfFile))
                .build();

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text())
                .containsIgnoringCase("Berlin")
                .containsIgnoringCase("capital")
                .containsIgnoringCase("Germany");
    }
}
