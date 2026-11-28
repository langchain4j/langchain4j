package dev.langchain4j.model.google.genai;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messageFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messageToJson;
import static dev.langchain4j.model.google.genai.GoogleGenAiInteractionsMapper.INTERACTION_STEPS_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.gaos.models.interactions.AllowedTools;
import com.google.genai.gaos.models.interactions.AudioContent;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.DocumentContent;
import com.google.genai.gaos.models.interactions.Error;
import com.google.genai.gaos.models.interactions.Function;
import com.google.genai.gaos.models.interactions.FunctionCallStep;
import com.google.genai.gaos.models.interactions.FunctionResultStep;
import com.google.genai.gaos.models.interactions.GenerationConfig;
import com.google.genai.gaos.models.interactions.GoogleSearch;
import com.google.genai.gaos.models.interactions.ImageContent;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionStatus;
import com.google.genai.gaos.models.interactions.MediaResolution;
import com.google.genai.gaos.models.interactions.Model;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.Step;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import com.google.genai.gaos.models.interactions.ThinkingLevel;
import com.google.genai.gaos.models.interactions.ThinkingSummaries;
import com.google.genai.gaos.models.interactions.ThoughtStep;
import com.google.genai.gaos.models.interactions.ToolChoiceConfig;
import com.google.genai.gaos.models.interactions.ToolChoiceType;
import com.google.genai.gaos.models.interactions.Usage;
import com.google.genai.gaos.models.interactions.UserInputStep;
import com.google.genai.gaos.models.interactions.VideoContent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleGenAiInteractionsMapperTest {

    @Test
    void should_reject_unsupported_parameters() {
        GoogleGenAiInteractionsChatRequestParameters withTopK = GoogleGenAiInteractionsChatRequestParameters.builder()
                .modelName("gemini-3.8-flash")
                .topK(40)
                .build();
        assertThatThrownBy(() -> GoogleGenAiInteractionsMapper.validateSupportedParameters(withTopK))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("topK");

        GoogleGenAiInteractionsChatRequestParameters withFreqPenalty =
                GoogleGenAiInteractionsChatRequestParameters.builder()
                        .modelName("gemini-3.8-flash")
                        .frequencyPenalty(0.5)
                        .build();
        assertThatThrownBy(() -> GoogleGenAiInteractionsMapper.validateSupportedParameters(withFreqPenalty))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("frequencyPenalty");

        GoogleGenAiInteractionsChatRequestParameters withPresencePenalty =
                GoogleGenAiInteractionsChatRequestParameters.builder()
                        .modelName("gemini-3.8-flash")
                        .presencePenalty(0.5)
                        .build();
        assertThatThrownBy(() -> GoogleGenAiInteractionsMapper.validateSupportedParameters(withPresencePenalty))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("presencePenalty");
    }

    @Test
    void should_map_request_parameters_tools_and_response_format() {
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("getWeather")
                .description("Gets weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        ResponseFormat jsonSchemaFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("WeatherOut")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("summary")
                                .required("summary")
                                .build())
                        .build())
                .build();

        GoogleGenAiInteractionsChatRequestParameters params = GoogleGenAiInteractionsChatRequestParameters.builder()
                .modelName("gemini-3.8-flash")
                .temperature(0.4)
                .topP(0.9)
                .maxOutputTokens(512)
                .stopSequences(List.of("END"))
                .thinkingLevel("MEDIUM")
                .includeThoughts(true)
                .serviceTier("FLEX")
                .labels(Map.of("env", "test"))
                .toolSpecifications(weatherTool)
                .toolChoice(ToolChoice.AUTO)
                .responseFormat(jsonSchemaFormat)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from("Rule 1"), SystemMessage.from("Rule 2"), UserMessage.from("Hello"))
                .parameters(params)
                .build();

        CreateModelInteraction mapped = GoogleGenAiInteractionsMapper.toCreateModelInteraction(
                chatRequest,
                params,
                null,
                null,
                null,
                42,
                true, // Google Search enabled -> combined with function tool -> VALIDATED
                false,
                false,
                false,
                List.of("getWeather"),
                null,
                null,
                null,
                false);

        assertThat(mapped.model()).contains(Model.of("gemini-3.8-flash"));
        assertThat(mapped.systemInstruction()).contains("Rule 1\nRule 2");
        assertThat(mapped.store()).contains(false);
        assertThat(mapped.stream()).contains(false);
        assertThat(mapped.labels()).contains(Map.of("env", "test"));
        assertThat(mapped.tools()).isPresent();
        assertThat(mapped.tools().get()).hasSize(2);
        assertThat(mapped.tools().get().get(0)).isInstanceOf(GoogleSearch.class);
        assertThat(mapped.tools().get().get(1)).isInstanceOf(Function.class);

        GenerationConfig genConfig = mapped.generationConfig().orElseThrow();
        assertThat(genConfig.temperature()).contains(0.4f);
        assertThat(genConfig.topP()).contains(0.9f);
        assertThat(genConfig.maxOutputTokens()).contains(512);
        assertThat(genConfig.stopSequences()).contains(List.of("END"));
        assertThat(genConfig.seed()).contains(42);
        assertThat(genConfig.thinkingLevel()).contains(ThinkingLevel.MEDIUM);
        assertThat(genConfig.thinkingSummaries()).contains(ThinkingSummaries.AUTO);

        ToolChoiceConfig choiceConfig =
                genConfig.toolChoice().orElseThrow().toolChoiceConfig().orElseThrow();
        AllowedTools allowed = choiceConfig.allowedTools().orElseThrow();
        assertThat(allowed.mode()).contains(ToolChoiceType.VALIDATED);
        assertThat(allowed.tools()).contains(List.of("getWeather"));

        TextResponseFormat respFormat = mapped.responseFormat()
                .orElseThrow()
                .responseFormat()
                .orElseThrow()
                .textResponseFormat()
                .orElseThrow();
        assertThat(respFormat.mimeType()).contains(TextResponseFormatMimeType.APPLICATION_JSON);
        assertThat(respFormat.schema()).isPresent();
        assertThat(respFormat.schema().get()).doesNotContainKey("strict");
    }

    @Test
    void should_map_multimodal_user_messages_and_tool_results() {
        UserMessage multimodalUser = UserMessage.from(
                dev.langchain4j.data.message.TextContent.from("Inspect these"),
                dev.langchain4j.data.message.ImageContent.from("aW1n", "image/png", DetailLevel.HIGH),
                dev.langchain4j.data.message.AudioContent.from("YXVk", "audio/mp3"),
                dev.langchain4j.data.message.VideoContent.from("dmlk", "video/mp4"),
                PdfFileContent.from("cGRm", "application/pdf"));

        ToolExecutionResultMessage multimodalToolResult = ToolExecutionResultMessage.builder()
                .id("call-1")
                .toolName("renderChart")
                .contents(List.of(
                        dev.langchain4j.data.message.TextContent.from("Chart ready"),
                        dev.langchain4j.data.message.ImageContent.from(Image.builder()
                                .base64Data("Y2hhcnQ=")
                                .mimeType("image/png")
                                .build())))
                .build();

        List<Step> steps = GoogleGenAiInteractionsMapper.toInputSteps(List.of(multimodalUser, multimodalToolResult));
        assertThat(steps).hasSize(2);

        UserInputStep userStep = (UserInputStep) steps.get(0);
        assertThat(userStep.content().orElseThrow()).hasSize(5);
        assertThat(userStep.content().get().get(0)).isInstanceOf(TextContent.class);
        ImageContent sdkImg = (ImageContent) userStep.content().get().get(1);
        assertThat(sdkImg.resolution()).contains(MediaResolution.HIGH);
        assertThat(userStep.content().get().get(2)).isInstanceOf(AudioContent.class);
        assertThat(userStep.content().get().get(3)).isInstanceOf(VideoContent.class);
        assertThat(userStep.content().get().get(4)).isInstanceOf(DocumentContent.class);

        FunctionResultStep resultStep = (FunctionResultStep) steps.get(1);
        assertThat(resultStep.callId()).contains("call-1");
        assertThat(resultStep.name()).contains("renderChart");
        assertThat(resultStep.result().orElseThrow().arrayOfFunctionResultSubcontent())
                .isPresent();
    }

    @Test
    void should_store_serialize_and_replay_interaction_steps_without_loss() {
        ThoughtStep thought = ThoughtStep.builder()
                .signature("sig-abc-123")
                .summary(List.of(
                        TextContent.builder().text("Thinking about weather").build()))
                .build();
        FunctionCallStep call1 = FunctionCallStep.builder()
                .id("call-1")
                .name("getWeather")
                .arguments(Map.of("city", "Indore"))
                .build();
        FunctionCallStep call2 = FunctionCallStep.builder()
                .id("call-2")
                .name("getWeather")
                .arguments(Map.of("city", "Pune"))
                .build();

        Interaction interaction = Interaction.builder()
                .status(InteractionStatus.REQUIRES_ACTION)
                .model(Model.of("gemini-3.8-flash"))
                .steps(List.of(thought, call1, call2))
                .usage(Usage.builder()
                        .totalInputTokens(20)
                        .totalOutputTokens(15)
                        .totalThoughtTokens(8)
                        .totalTokens(43)
                        .build())
                .build();

        ChatResponse response = GoogleGenAiInteractionsMapper.toChatResponse(interaction, "gemini-3.8-flash", true);
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(response.aiMessage().thinking()).isEqualTo("Thinking about weather");
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(2);
        assertThat(response.aiMessage().attributes()).containsKey(INTERACTION_STEPS_ATTRIBUTE);

        GoogleGenAiTokenUsage usage = (GoogleGenAiTokenUsage) response.tokenUsage();
        assertThat(usage.inputTokenCount()).isEqualTo(20);
        assertThat(usage.thoughtsTokenCount()).isEqualTo(8);

        // Round-trip through ChatMessageSerializer / ChatMessageDeserializer (ChatMemoryStore simulation)
        String json = messageToJson(response.aiMessage());
        ChatMessage restored = messageFromJson(json);

        List<Step> replayedSteps = GoogleGenAiInteractionsMapper.toInputSteps(List.of(
                UserMessage.from("Weather in Indore and Pune?"),
                restored,
                ToolExecutionResultMessage.from("call-1", "getWeather", "28C"),
                ToolExecutionResultMessage.from("call-2", "getWeather", "26C")));

        assertThat(replayedSteps).hasSize(6);
        assertThat(replayedSteps.get(1)).isEqualTo(thought);
        assertThat(replayedSteps.get(2)).isEqualTo(call1);
        assertThat(replayedSteps.get(3)).isEqualTo(call2);
    }

    @Test
    void should_reject_tool_calls_without_steps_and_tampered_turns() {
        AiMessage handBuiltToolTurn = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-99")
                .name("getWeather")
                .arguments("{\"city\":\"Delhi\"}")
                .build());

        assertThatThrownBy(() -> GoogleGenAiInteractionsMapper.toInputSteps(List.of(handBuiltToolTurn)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("no Gemini interaction_steps");

        Interaction interaction = Interaction.builder()
                .status(InteractionStatus.COMPLETED)
                .steps(List.of(ModelOutputStep.builder()
                        .content(List.of(
                                TextContent.builder().text("Original answer").build()))
                        .build()))
                .build();

        AiMessage generated = GoogleGenAiInteractionsMapper.toChatResponse(interaction, "gemini-3.8-flash", false)
                .aiMessage();
        AiMessage tampered = generated.toBuilder().text("Modified answer").build();

        assertThatThrownBy(() -> GoogleGenAiInteractionsMapper.toInputSteps(List.of(tampered)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modified after generation");
    }

    @Test
    void should_map_content_blocked_errors_to_ContentFilteredException() {
        Interaction failedInteraction = Interaction.builder()
                .status(InteractionStatus.FAILED)
                .errors(List.of(Error.builder()
                        .code("content_blocked")
                        .message("Blocked by safety policy")
                        .build()))
                .build();

        assertThatThrownBy(() ->
                        GoogleGenAiInteractionsMapper.toChatResponse(failedInteraction, "gemini-3.8-flash", false))
                .isInstanceOf(ContentFilteredException.class)
                .hasMessage("Blocked by safety policy");
    }
}
