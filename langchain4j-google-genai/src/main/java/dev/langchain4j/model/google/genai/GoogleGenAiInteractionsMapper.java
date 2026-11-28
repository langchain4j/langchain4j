package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.gaos.models.interactions.AllowedTools;
import com.google.genai.gaos.models.interactions.AudioContentMimeType;
import com.google.genai.gaos.models.interactions.CodeExecution;
import com.google.genai.gaos.models.interactions.Content;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.CreateModelInteractionResponseFormat;
import com.google.genai.gaos.models.interactions.DocumentContent;
import com.google.genai.gaos.models.interactions.DocumentContentMimeType;
import com.google.genai.gaos.models.interactions.Error;
import com.google.genai.gaos.models.interactions.Function;
import com.google.genai.gaos.models.interactions.FunctionCallStep;
import com.google.genai.gaos.models.interactions.FunctionResultStep;
import com.google.genai.gaos.models.interactions.FunctionResultStepResultUnion;
import com.google.genai.gaos.models.interactions.FunctionResultSubcontent;
import com.google.genai.gaos.models.interactions.GenerationConfig;
import com.google.genai.gaos.models.interactions.GoogleMaps;
import com.google.genai.gaos.models.interactions.GoogleSearch;
import com.google.genai.gaos.models.interactions.ImageContentMimeType;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionStatus;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.MediaResolution;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.ResponseFormat;
import com.google.genai.gaos.models.interactions.SafetySetting;
import com.google.genai.gaos.models.interactions.ServiceTier;
import com.google.genai.gaos.models.interactions.Step;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import com.google.genai.gaos.models.interactions.ThinkingLevel;
import com.google.genai.gaos.models.interactions.ThinkingSummaries;
import com.google.genai.gaos.models.interactions.ThoughtStep;
import com.google.genai.gaos.models.interactions.ThoughtSummaryContent;
import com.google.genai.gaos.models.interactions.Tool;
import com.google.genai.gaos.models.interactions.ToolChoiceConfig;
import com.google.genai.gaos.models.interactions.ToolChoiceType;
import com.google.genai.gaos.models.interactions.URLContext;
import com.google.genai.gaos.models.interactions.Usage;
import com.google.genai.gaos.models.interactions.UserInputStep;
import com.google.genai.gaos.models.interactions.VideoContentMimeType;
import com.google.genai.gaos.utils.JSON;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

class GoogleGenAiInteractionsMapper {

    static final String INTERACTION_STEPS_ATTRIBUTE = "interaction_steps";

    private static final ObjectMapper SDK_MAPPER = JSON.getMapper();

    private static final Set<String> CONTENT_BLOCKED_CODES = Set.of(
            "safety",
            "prohibited_content",
            "blocklist",
            "content_blocked",
            "recitation",
            "spii",
            "image_safety",
            "image_prohibited_content");

    private GoogleGenAiInteractionsMapper() {}

    static void validateSupportedParameters(GoogleGenAiInteractionsChatRequestParameters parameters) {
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException("topK is not supported by the Gemini Interactions API");
        }
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException("frequencyPenalty is not supported by the Gemini Interactions API");
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException("presencePenalty is not supported by the Gemini Interactions API");
        }
    }

    static CreateModelInteraction toCreateModelInteraction(
            ChatRequest chatRequest,
            GoogleGenAiInteractionsChatRequestParameters parameters,
            List<SafetySetting> defaultSafetySettings,
            String defaultThinkingLevel,
            Boolean defaultIncludeThoughts,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            boolean codeExecutionEnabled,
            List<String> allowedFunctionNames,
            Map<String, String> defaultLabels,
            String defaultServiceTier,
            Consumer<CreateModelInteraction.Builder> customizer,
            boolean stream) {

        validateSupportedParameters(parameters);

        String modelName = parameters.modelName();
        if (isNullOrBlank(modelName)) {
            throw new InvalidRequestException("modelName is required");
        }

        List<Step> inputSteps = toInputSteps(chatRequest.messages());
        String systemInstruction = toSystemInstruction(chatRequest.messages());

        List<Tool> tools = new ArrayList<>();
        boolean hasBuiltInTools = false;
        if (googleSearchEnabled) {
            tools.add(GoogleSearch.builder().build());
            hasBuiltInTools = true;
        }
        if (googleMapsEnabled) {
            tools.add(GoogleMaps.builder().build());
            hasBuiltInTools = true;
        }
        if (urlContextEnabled) {
            tools.add(URLContext.builder().build());
            hasBuiltInTools = true;
        }
        if (codeExecutionEnabled) {
            tools.add(CodeExecution.builder().build());
            hasBuiltInTools = true;
        }

        boolean hasFunctionTools = !isNullOrEmpty(parameters.toolSpecifications());
        if (hasFunctionTools) {
            for (ToolSpecification spec : parameters.toolSpecifications()) {
                tools.add(toFunctionTool(spec));
            }
        }

        GenerationConfig.Builder genConfigBuilder = GenerationConfig.builder();
        boolean hasGenConfig = false;

        if (parameters.temperature() != null) {
            genConfigBuilder.temperature(parameters.temperature().floatValue());
            hasGenConfig = true;
        }
        if (parameters.topP() != null) {
            genConfigBuilder.topP(parameters.topP().floatValue());
            hasGenConfig = true;
        }
        if (parameters.maxOutputTokens() != null) {
            genConfigBuilder.maxOutputTokens(parameters.maxOutputTokens());
            hasGenConfig = true;
        }
        if (!isNullOrEmpty(parameters.stopSequences())) {
            genConfigBuilder.stopSequences(parameters.stopSequences());
            hasGenConfig = true;
        }
        if (seed != null) {
            genConfigBuilder.seed(seed);
            hasGenConfig = true;
        }

        String effectiveThinkingLevel =
                parameters.thinkingLevel() != null ? parameters.thinkingLevel() : defaultThinkingLevel;
        if (!isNullOrBlank(effectiveThinkingLevel)) {
            genConfigBuilder.thinkingLevel(ThinkingLevel.of(effectiveThinkingLevel.toLowerCase()));
            hasGenConfig = true;
        }

        Boolean effectiveIncludeThoughts =
                parameters.includeThoughts() != null ? parameters.includeThoughts() : defaultIncludeThoughts;
        if (effectiveIncludeThoughts != null) {
            genConfigBuilder.thinkingSummaries(
                    effectiveIncludeThoughts ? ThinkingSummaries.AUTO : ThinkingSummaries.NONE);
            hasGenConfig = true;
        }

        com.google.genai.gaos.models.interactions.ToolChoice mappedToolChoice =
                toToolChoice(parameters.toolChoice(), allowedFunctionNames, hasBuiltInTools, hasFunctionTools);
        if (mappedToolChoice != null) {
            genConfigBuilder.toolChoice(mappedToolChoice);
            hasGenConfig = true;
        }

        CreateModelInteraction.Builder builder =
                CreateModelInteraction.builder()
                        .model(modelName)
                        .input(InteractionsInput.ofStep(inputSteps))
                        .store(false)
                        .stream(stream);

        if (systemInstruction != null) {
            builder.systemInstruction(systemInstruction);
        }
        if (!tools.isEmpty()) {
            builder.tools(tools);
        }
        if (hasGenConfig) {
            builder.generationConfig(genConfigBuilder.build());
        }

        CreateModelInteractionResponseFormat mappedResponseFormat = toResponseFormat(parameters.responseFormat());
        if (mappedResponseFormat != null) {
            builder.responseFormat(mappedResponseFormat);
        }

        List<SafetySetting> effectiveSafetySettings =
                !isNullOrEmpty(parameters.safetySettings()) ? parameters.safetySettings() : defaultSafetySettings;
        if (!isNullOrEmpty(effectiveSafetySettings)) {
            builder.safetySettings(effectiveSafetySettings);
        }

        Map<String, String> effectiveLabels = !isNullOrEmpty(parameters.labels()) ? parameters.labels() : defaultLabels;
        if (!isNullOrEmpty(effectiveLabels)) {
            builder.labels(effectiveLabels);
        }

        String effectiveServiceTier =
                !isNullOrBlank(parameters.serviceTier()) ? parameters.serviceTier() : defaultServiceTier;
        if (!isNullOrBlank(effectiveServiceTier)) {
            builder.serviceTier(ServiceTier.of(effectiveServiceTier.toLowerCase()));
        }

        if (customizer != null) {
            customizer.accept(builder);
        }

        CreateModelInteraction built = builder.build();
        validateCustomizerInvariants(built, stream);
        return built;
    }

    static void validateCustomizerInvariants(CreateModelInteraction request, boolean expectedStream) {
        if (!Boolean.FALSE.equals(request.store().orElse(null))) {
            throw new IllegalStateException("CreateModelInteraction must have store=false");
        }
        if (Boolean.TRUE.equals(request.background().orElse(false))) {
            throw new IllegalStateException("CreateModelInteraction must not set background=true");
        }
        if (request.previousInteractionId().isPresent()) {
            throw new IllegalStateException("CreateModelInteraction must not set previous_interaction_id");
        }
        if (!Objects.equals(expectedStream, request.stream().orElse(null))) {
            throw new IllegalStateException("CreateModelInteraction stream must be " + expectedStream);
        }
    }

    static @Nullable String toSystemInstruction(List<ChatMessage> messages) {
        String joined = messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .map(SystemMessage::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }

    static List<Step> toInputSteps(List<ChatMessage> messages) {
        List<Step> steps = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                continue;
            } else if (message instanceof UserMessage userMessage) {
                steps.add(toUserInputStep(userMessage));
            } else if (message instanceof AiMessage aiMessage) {
                steps.addAll(toAiReplaySteps(aiMessage));
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                steps.add(toFunctionResultStep(toolResult));
            } else {
                throw new UnsupportedFeatureException(
                        "Unsupported message type: " + message.getClass().getName());
            }
        }
        return steps;
    }

    private static UserInputStep toUserInputStep(UserMessage userMessage) {
        List<Content> contents = new ArrayList<>();
        for (dev.langchain4j.data.message.Content content : userMessage.contents()) {
            if (content instanceof TextContent textContent) {
                contents.add(com.google.genai.gaos.models.interactions.TextContent.builder()
                        .text(textContent.text())
                        .build());
            } else if (content instanceof ImageContent imageContent) {
                contents.add(toSdkImageContent(imageContent.image(), imageContent.detailLevel()));
            } else if (content instanceof AudioContent audioContent) {
                contents.add(toSdkAudioContent(audioContent.audio()));
            } else if (content instanceof VideoContent videoContent) {
                contents.add(toSdkVideoContent(videoContent.video()));
            } else if (content instanceof PdfFileContent pdfContent) {
                contents.add(toSdkDocumentContent(pdfContent.pdfFile()));
            } else {
                throw new UnsupportedFeatureException(
                        "Unsupported user content type: " + content.getClass().getName());
            }
        }
        return UserInputStep.builder().content(contents).build();
    }

    private static com.google.genai.gaos.models.interactions.ImageContent toSdkImageContent(
            Image image, @Nullable DetailLevel detailLevel) {
        com.google.genai.gaos.models.interactions.ImageContent.Builder builder =
                com.google.genai.gaos.models.interactions.ImageContent.builder();
        if (!isNullOrBlank(image.base64Data())) {
            builder.data(image.base64Data());
        } else if (image.url() != null) {
            builder.uri(image.url().toString());
        }
        if (!isNullOrBlank(image.mimeType())) {
            builder.mimeType(ImageContentMimeType.of(image.mimeType()));
        }
        if (detailLevel != null) {
            builder.resolution(toMediaResolution(detailLevel));
        }
        return builder.build();
    }

    private static MediaResolution toMediaResolution(DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> MediaResolution.LOW;
            case HIGH -> MediaResolution.HIGH;
            default -> MediaResolution.MEDIUM;
        };
    }

    private static com.google.genai.gaos.models.interactions.AudioContent toSdkAudioContent(Audio audio) {
        com.google.genai.gaos.models.interactions.AudioContent.Builder builder =
                com.google.genai.gaos.models.interactions.AudioContent.builder();
        if (!isNullOrBlank(audio.base64Data())) {
            builder.data(audio.base64Data());
        } else if (audio.url() != null) {
            builder.uri(audio.url().toString());
        }
        if (!isNullOrBlank(audio.mimeType())) {
            builder.mimeType(AudioContentMimeType.of(audio.mimeType()));
        }
        return builder.build();
    }

    private static com.google.genai.gaos.models.interactions.VideoContent toSdkVideoContent(Video video) {
        com.google.genai.gaos.models.interactions.VideoContent.Builder builder =
                com.google.genai.gaos.models.interactions.VideoContent.builder();
        if (!isNullOrBlank(video.base64Data())) {
            builder.data(video.base64Data());
        } else if (video.url() != null) {
            builder.uri(video.url().toString());
        }
        if (!isNullOrBlank(video.mimeType())) {
            builder.mimeType(VideoContentMimeType.of(video.mimeType()));
        }
        return builder.build();
    }

    private static DocumentContent toSdkDocumentContent(PdfFile pdfFile) {
        DocumentContent.Builder builder = DocumentContent.builder();
        if (!isNullOrBlank(pdfFile.base64Data())) {
            builder.data(pdfFile.base64Data());
        } else if (pdfFile.url() != null) {
            builder.uri(pdfFile.url().toString());
        }
        builder.mimeType(DocumentContentMimeType.APPLICATION_PDF);
        return builder.build();
    }

    private static List<Step> toAiReplaySteps(AiMessage aiMessage) {
        Object rawSteps = aiMessage.attributes().get(INTERACTION_STEPS_ATTRIBUTE);
        List<String> serializedSteps = rawSteps instanceof List<?> list
                ? list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList()
                : Collections.emptyList();

        if (serializedSteps.isEmpty()) {
            if (aiMessage.hasToolExecutionRequests()) {
                throw new UnsupportedFeatureException(
                        "Cannot replay AiMessage with tool execution requests that has no Gemini interaction_steps");
            }
            if (!isNullOrEmpty(aiMessage.text())) {
                return List.of(ModelOutputStep.builder()
                        .content(List.of(com.google.genai.gaos.models.interactions.TextContent.builder()
                                .text(aiMessage.text())
                                .build()))
                        .build());
            }
            return Collections.emptyList();
        }

        List<Step> deserializedSteps = new ArrayList<>(serializedSteps.size());
        for (String stepJson : serializedSteps) {
            try {
                deserializedSteps.add(SDK_MAPPER.readValue(stepJson, Step.class));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to deserialize stored interaction step", e);
            }
        }

        verifyStoredStepsMatchAiMessage(aiMessage, deserializedSteps);
        return deserializedSteps;
    }

    private static void verifyStoredStepsMatchAiMessage(AiMessage aiMessage, List<Step> storedSteps) {
        StringBuilder storedText = new StringBuilder();
        List<ToolExecutionRequest> storedToolCalls = new ArrayList<>();

        for (Step step : storedSteps) {
            if (step instanceof ModelOutputStep outputStep) {
                outputStep.content().ifPresent(contents -> {
                    for (Content content : contents) {
                        if (content instanceof com.google.genai.gaos.models.interactions.TextContent tc) {
                            tc.text().ifPresent(storedText::append);
                        }
                    }
                });
            } else if (step instanceof FunctionCallStep callStep) {
                String id = callStep.id().orElse(null);
                String name = callStep.name().orElse(null);
                Map<String, Object> args = callStep.arguments().orElse(Collections.emptyMap());
                storedToolCalls.add(ToolExecutionRequest.builder()
                        .id(id)
                        .name(name)
                        .arguments(Json.toJson(args))
                        .build());
            }
        }

        String actualText = aiMessage.text() != null ? aiMessage.text() : "";
        if (!Objects.equals(storedText.toString(), actualText)) {
            throw new IllegalArgumentException(
                    "AiMessage text was modified after generation and no longer matches stored interaction_steps");
        }

        List<ToolExecutionRequest> actualToolCalls =
                aiMessage.toolExecutionRequests() != null ? aiMessage.toolExecutionRequests() : Collections.emptyList();
        if (storedToolCalls.size() != actualToolCalls.size()) {
            throw new IllegalArgumentException(
                    "AiMessage toolExecutionRequests were modified after generation and no longer match stored interaction_steps");
        }
        for (int i = 0; i < storedToolCalls.size(); i++) {
            ToolExecutionRequest expected = storedToolCalls.get(i);
            ToolExecutionRequest actual = actualToolCalls.get(i);
            if (!Objects.equals(expected.id(), actual.id())
                    || !Objects.equals(expected.name(), actual.name())
                    || !Objects.equals(parseArgsMap(expected.arguments()), parseArgsMap(actual.arguments()))) {
                throw new IllegalArgumentException(
                        "AiMessage toolExecutionRequests were modified after generation and no longer match stored interaction_steps");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgsMap(@Nullable String json) {
        if (isNullOrBlank(json)) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = Json.fromJson(json, Map.class);
        return parsed != null ? parsed : Collections.emptyMap();
    }

    private static FunctionResultStep toFunctionResultStep(ToolExecutionResultMessage toolResult) {
        FunctionResultStep.Builder builder = FunctionResultStep.builder()
                .callId(toolResult.id())
                .name(toolResult.toolName())
                .isError(Boolean.TRUE.equals(toolResult.isError()));

        List<dev.langchain4j.data.message.Content> contents = toolResult.contents();
        if (contents.size() == 1 && contents.get(0) instanceof TextContent singleText) {
            builder.result(FunctionResultStepResultUnion.of(singleText.text()));
        } else {
            List<FunctionResultSubcontent> subcontents = new ArrayList<>(contents.size());
            for (dev.langchain4j.data.message.Content content : contents) {
                if (content instanceof TextContent textContent) {
                    subcontents.add(com.google.genai.gaos.models.interactions.TextContent.builder()
                            .text(textContent.text())
                            .build());
                } else if (content instanceof ImageContent imageContent) {
                    subcontents.add(toSdkImageContent(imageContent.image(), imageContent.detailLevel()));
                } else if (content instanceof AudioContent audioContent) {
                    subcontents.add(toSdkAudioContent(audioContent.audio()));
                } else if (content instanceof VideoContent videoContent) {
                    subcontents.add(toSdkVideoContent(videoContent.video()));
                } else if (content instanceof PdfFileContent pdfContent) {
                    subcontents.add(toSdkDocumentContent(pdfContent.pdfFile()));
                } else {
                    throw new UnsupportedFeatureException("Unsupported tool result content type: "
                            + content.getClass().getName());
                }
            }
            builder.result(FunctionResultStepResultUnion.of(subcontents));
        }

        return builder.build();
    }

    private static Function toFunctionTool(ToolSpecification spec) {
        Function.Builder builder = Function.builder().name(spec.name());
        if (!isNullOrBlank(spec.description())) {
            builder.description(spec.description());
        }
        if (spec.parameters() != null) {
            Map<String, Object> schemaMap = new LinkedHashMap<>(toMap(spec.parameters()));
            schemaMap.remove("strict");
            builder.parameters(schemaMap);
        }
        return builder.build();
    }

    private static com.google.genai.gaos.models.interactions.@Nullable ToolChoice toToolChoice(
            @Nullable ToolChoice toolChoice,
            @Nullable List<String> allowedFunctionNames,
            boolean hasBuiltInTools,
            boolean hasFunctionTools) {

        boolean hasAllowedNames = !isNullOrEmpty(allowedFunctionNames);
        if (toolChoice == null && !hasAllowedNames) {
            return null;
        }

        ToolChoiceType mode = null;
        if (toolChoice != null) {
            mode = switch (toolChoice) {
                case AUTO -> (hasBuiltInTools && hasFunctionTools) ? ToolChoiceType.VALIDATED : ToolChoiceType.AUTO;
                case REQUIRED -> ToolChoiceType.ANY;
                case NONE -> ToolChoiceType.NONE;
            };
        }

        if (hasAllowedNames) {
            AllowedTools.Builder allowedBuilder = AllowedTools.builder().tools(allowedFunctionNames);
            if (mode != null) {
                allowedBuilder.mode(mode);
            }
            return com.google.genai.gaos.models.interactions.ToolChoice.of(ToolChoiceConfig.builder()
                    .allowedTools(allowedBuilder.build())
                    .build());
        }

        return com.google.genai.gaos.models.interactions.ToolChoice.of(mode);
    }

    private static @Nullable CreateModelInteractionResponseFormat toResponseFormat(
            dev.langchain4j.model.chat.request.@Nullable ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == ResponseFormatType.TEXT) {
            return null;
        }
        if (responseFormat.type() == ResponseFormatType.JSON) {
            TextResponseFormat.Builder textFormatBuilder =
                    TextResponseFormat.builder().mimeType(TextResponseFormatMimeType.APPLICATION_JSON);
            JsonSchema jsonSchema = responseFormat.jsonSchema();
            if (jsonSchema != null && jsonSchema.rootElement() != null) {
                Map<String, Object> schemaMap = new LinkedHashMap<>(toMap(jsonSchema.rootElement()));
                schemaMap.remove("strict");
                textFormatBuilder.schema(schemaMap);
            }
            return CreateModelInteractionResponseFormat.of(ResponseFormat.of(textFormatBuilder.build()));
        }
        throw new UnsupportedFeatureException("Unsupported response format: " + responseFormat.type());
    }

    static ChatResponse toChatResponse(Interaction interaction, String requestedModelName, boolean returnThinking) {
        InteractionStatus status = interaction.status().orElse(null);
        if (InteractionStatus.FAILED.equals(status)) {
            throw mapInteractionErrors(interaction.errors().orElse(Collections.emptyList()));
        }

        List<Step> steps = interaction.steps().orElse(Collections.emptyList());
        StringBuilder outputText = new StringBuilder();
        StringBuilder thinkingText = new StringBuilder();
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        List<String> serializedSteps = new ArrayList<>(steps.size());

        for (Step step : steps) {
            try {
                serializedSteps.add(SDK_MAPPER.writeValueAsString(step));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize interaction step", e);
            }

            if (step instanceof ModelOutputStep outputStep) {
                outputStep.content().ifPresent(contents -> {
                    for (Content content : contents) {
                        if (content instanceof com.google.genai.gaos.models.interactions.TextContent tc) {
                            tc.text().ifPresent(outputText::append);
                        }
                    }
                });
            } else if (step instanceof FunctionCallStep callStep) {
                String id = callStep.id().orElse(null);
                String name = callStep.name().orElse(null);
                Map<String, Object> args = callStep.arguments().orElse(Collections.emptyMap());
                toolExecutionRequests.add(ToolExecutionRequest.builder()
                        .id(id)
                        .name(name)
                        .arguments(Json.toJson(args))
                        .build());
            } else if (step instanceof ThoughtStep thoughtStep && returnThinking) {
                thoughtStep.summary().ifPresent(summaries -> {
                    for (ThoughtSummaryContent summaryContent : summaries) {
                        if (summaryContent instanceof com.google.genai.gaos.models.interactions.TextContent tc) {
                            tc.text().ifPresent(thinkingText::append);
                        }
                    }
                });
            }
        }

        if (outputText.isEmpty() && interaction.outputText().isPresent()) {
            outputText.append(interaction.outputText().get());
        }

        Map<String, Object> attributes = new HashMap<>();
        if (!serializedSteps.isEmpty()) {
            attributes.put(INTERACTION_STEPS_ATTRIBUTE, serializedSteps);
        }

        AiMessage.Builder aiMessageBuilder = AiMessage.builder().attributes(attributes);
        if (!outputText.isEmpty()) {
            aiMessageBuilder.text(outputText.toString());
        }
        if (!toolExecutionRequests.isEmpty()) {
            aiMessageBuilder.toolExecutionRequests(toolExecutionRequests);
        }
        if (returnThinking && !thinkingText.isEmpty()) {
            aiMessageBuilder.thinking(thinkingText.toString());
        }

        FinishReason finishReason = toFinishReason(status, !toolExecutionRequests.isEmpty());
        GoogleGenAiTokenUsage tokenUsage = toTokenUsage(interaction.usage().orElse(null));

        String responseModel = interaction
                .model()
                .map(m -> m.value())
                .filter(v -> !isNullOrBlank(v))
                .orElse(requestedModelName);

        String interactionId = interaction.id().filter(id -> !isNullOrBlank(id)).orElse(null);

        GoogleGenAiInteractionsChatResponseMetadata metadata = GoogleGenAiInteractionsChatResponseMetadata.builder()
                .id(interactionId)
                .modelName(responseModel)
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .rawResponse(interaction)
                .status(status)
                .build();

        return ChatResponse.builder()
                .aiMessage(aiMessageBuilder.build())
                .metadata(metadata)
                .build();
    }

    static FinishReason toFinishReason(@Nullable InteractionStatus status, boolean hasFunctionCalls) {
        if (status == null) {
            return OTHER;
        }
        if (InteractionStatus.REQUIRES_ACTION.equals(status)) {
            return TOOL_EXECUTION;
        }
        if (InteractionStatus.COMPLETED.equals(status)) {
            return hasFunctionCalls ? TOOL_EXECUTION : STOP;
        }
        if (InteractionStatus.INCOMPLETE.equals(status) || InteractionStatus.REQUIRES_CONTINUATION.equals(status)) {
            return LENGTH;
        }
        return OTHER;
    }

    static GoogleGenAiTokenUsage toTokenUsage(@Nullable Usage usage) {
        if (usage == null) {
            return GoogleGenAiTokenUsage.builder().build();
        }
        return GoogleGenAiTokenUsage.builder()
                .inputTokenCount(usage.totalInputTokens().orElse(null))
                .outputTokenCount(usage.totalOutputTokens().orElse(null))
                .totalTokenCount(usage.totalTokens().orElse(null))
                .cachedContentTokenCount(usage.totalCachedTokens().orElse(null))
                .thoughtsTokenCount(usage.totalThoughtTokens().orElse(null))
                .toolUsePromptTokenCount(usage.totalToolUseTokens().orElse(null))
                .initialPromptTokenCount(usage.initialInputTokens().orElse(null))
                .build();
    }

    static RuntimeException mapInteractionErrors(List<Error> errors) {
        if (isNullOrEmpty(errors)) {
            return new RuntimeException("Interaction failed without error details");
        }
        Error primary = errors.get(0);
        String code = primary.code().orElse("");
        String message = primary.message().orElse("Interaction failed with code: " + code);

        if (CONTENT_BLOCKED_CODES.contains(code.toLowerCase())) {
            return new ContentFilteredException(message);
        }
        if ("invalid_argument".equalsIgnoreCase(code)) {
            return new InvalidRequestException(message);
        }
        return new dev.langchain4j.exception.LangChain4jException(message);
    }
}
