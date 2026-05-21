package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;
import static java.util.Arrays.asList;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonField;
import com.openai.core.JsonMissing;
import com.openai.core.JsonValue;
import com.openai.credential.Credential;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseError;
import com.openai.models.responses.ResponseErrorEvent;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFunctionCallArgumentsDeltaEvent;
import com.openai.models.responses.ResponseFunctionCallArgumentsDoneEvent;
import com.openai.models.responses.ResponseFunctionCallOutputItem;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseIncompleteEvent;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputImageContent;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseInputTextContent;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputItemAddedEvent;
import com.openai.models.responses.ResponseOutputItemDoneEvent;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.openai.models.responses.ResponseReasoningTextDeltaEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolChoiceOptions;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.ToolSpecificationUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StreamingChatModel implementation using the official OpenAI Java client for the Responses API.
 */
@Experimental
public class OpenAiOfficialResponsesStreamingChatModel implements StreamingChatModel {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiOfficialResponsesStreamingChatModel.class);
    private static final String PROMPT_CACHE_RETENTION_FIELD = "prompt_cache_retention";
    // do not change, will break backward compatibility!
    static final String ENCRYPTED_REASONING_KEY = "encrypted_reasoning";

    private final OpenAIClient client;
    private final ExecutorService executorService;
    private final OpenAiOfficialResponsesChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;

    private OpenAiOfficialResponsesStreamingChatModel(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : setupSyncClient(
                        builder.baseUrl,
                        builder.apiKey,
                        builder.credential,
                        builder.microsoftFoundryDeploymentName,
                        builder.azureOpenAIServiceVersion,
                        builder.organizationId,
                        builder.isMicrosoftFoundry,
                        builder.isGitHubModels,
                        builder.modelName,
                        builder.timeout,
                        builder.maxRetries,
                        builder.proxy,
                        builder.customHeaders);
        this.executorService =
                getOrDefault(builder.executorService, DefaultExecutorProvider::getDefaultExecutorService);

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        OpenAiOfficialResponsesChatRequestParameters responsesParameters =
                commonParameters instanceof OpenAiOfficialResponsesChatRequestParameters p
                        ? p
                        : OpenAiOfficialResponsesChatRequestParameters.EMPTY;

        this.defaultRequestParameters = OpenAiOfficialResponsesChatRequestParameters.builder()
                .modelName(ensureNotNull(getOrDefault(builder.modelName, commonParameters.modelName()), "modelName"))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens()))
                .toolSpecifications(getOrDefault(builder.toolSpecifications, commonParameters.toolSpecifications()))
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .previousResponseId(getOrDefault(builder.previousResponseId, responsesParameters.previousResponseId()))
                .maxToolCalls(getOrDefault(builder.maxToolCalls, responsesParameters.maxToolCalls()))
                .parallelToolCalls(getOrDefault(builder.parallelToolCalls, responsesParameters.parallelToolCalls()))
                .topLogprobs(getOrDefault(builder.topLogprobs, responsesParameters.topLogprobs()))
                .truncation(getOrDefault(builder.truncation, responsesParameters.truncation()))
                .include(getOrDefault(builder.include, responsesParameters.include()))
                .serviceTier(getOrDefault(builder.serviceTier, responsesParameters.serviceTier()))
                .safetyIdentifier(getOrDefault(builder.safetyIdentifier, responsesParameters.safetyIdentifier()))
                .promptCacheKey(getOrDefault(builder.promptCacheKey, responsesParameters.promptCacheKey()))
                .promptCacheRetention(
                        getOrDefault(builder.promptCacheRetention, responsesParameters.promptCacheRetention()))
                .reasoningEffort(getOrDefault(builder.reasoningEffort, responsesParameters.reasoningEffort()))
                .reasoningSummary(getOrDefault(builder.reasoningSummary, responsesParameters.reasoningSummary()))
                .textVerbosity(getOrDefault(builder.textVerbosity, responsesParameters.textVerbosity()))
                .streamIncludeObfuscation(
                        getOrDefault(builder.streamIncludeObfuscation, responsesParameters.streamIncludeObfuscation()))
                .store(getOrDefault(builder.store, getOrDefault(responsesParameters.store(), false)))
                .strictTools(getOrDefault(builder.strictTools, responsesParameters.strictTools()))
                .strictJsonSchema(getOrDefault(builder.strictJsonSchema, responsesParameters.strictJsonSchema()))
                .serverTools(getOrDefault(builder.serverTools, responsesParameters.serverTools()))
                .build();

        this.listeners = copy(builder.listeners);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        validate(chatRequest.parameters());
        OpenAiOfficialResponsesChatRequestParameters parameters =
                (OpenAiOfficialResponsesChatRequestParameters) chatRequest.parameters();

        AtomicReference<String> responseIdRef = new AtomicReference<>();
        Future<?> streamingFuture = null;

        try {
            var params = buildRequestParams(chatRequest, parameters);
            var streamResponse = client.responses().createStreaming(params);

            ResponsesStreamingHandle streamingHandle = new ResponsesStreamingHandle(() -> {
                try {
                    streamResponse.close();
                } catch (Exception e) {
                    // Ignore close errors
                }
            });

            var eventHandler =
                    new ResponsesEventHandler(handler, responseIdRef, parameters.modelName(), streamingHandle);

            // The forEach call blocks, so it is submitted to the executor service to run asynchronously.
            // We keep this on our executor (instead of OpenAIClientAsync callbacks) to ensure that user
            // handlers execute on a single, controlled thread rather than SDK/OkHttp threads.
            streamingFuture = executorService.submit(() -> {
                try (streamResponse) {
                    streamResponse.stream().forEach(eventHandler::handleEvent);
                } catch (CancellationException e) {
                    withLoggingExceptions(() -> handler.onError(e));
                } catch (Exception e) {
                    RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(e);
                    withLoggingExceptions(() -> handler.onError(mappedException));
                } finally {
                    streamingHandle.markCompleted();
                }
            });
            streamingHandle.setStreamingFuture(streamingFuture);
        } catch (Exception e) {
            RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(e);
            withLoggingExceptions(() -> handler.onError(mappedException));
            if (streamingFuture != null) {
                streamingFuture.cancel(true);
            }
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    static String extractReasoningSummary(com.openai.models.responses.Response response) {
        StringBuilder summaryBuilder = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (item.isReasoning()) {
                for (ResponseReasoningItem.Summary summary : item.asReasoning().summary()) {
                    summaryBuilder.append(summary.text());
                }
            }
        }
        return summaryBuilder.isEmpty() ? null : summaryBuilder.toString();
    }

    static String extractEncryptedReasoning(com.openai.models.responses.Response response) {
        for (ResponseOutputItem item : response.output()) {
            if (item.isReasoning()) {
                var encrypted = item.asReasoning().encryptedContent();
                if (encrypted.isPresent() && !encrypted.get().isEmpty()) {
                    return encrypted.get();
                }
            }
        }
        return null;
    }

    static List<ToolExecutionRequest> extractToolExecutionRequests(com.openai.models.responses.Response response) {
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (ResponseOutputItem item : response.output()) {
            if (item.isFunctionCall()) {
                var fn = item.asFunctionCall();
                requests.add(ToolExecutionRequest.builder()
                        .id(fn.callId())
                        .name(fn.name())
                        .arguments(fn.arguments())
                        .build());
            }
        }
        return requests;
    }

    static String extractText(com.openai.models.responses.Response response) {
        StringBuilder textBuilder = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (item.isMessage()) {
                item.asMessage().content().forEach(content -> {
                    if (content.isOutputText()) {
                        textBuilder.append(content.asOutputText().text());
                    }
                });
            }
        }
        return textBuilder.isEmpty() ? null : textBuilder.toString();
    }

    static AiMessage buildAiMessage(
            String text, String thinking, List<ToolExecutionRequest> toolExecutionRequests, String encryptedReasoning) {
        AiMessage.Builder builder =
                AiMessage.builder().text(text).thinking(thinking).toolExecutionRequests(toolExecutionRequests);
        if (encryptedReasoning != null) {
            builder.attributes(Map.of(ENCRYPTED_REASONING_KEY, encryptedReasoning));
        }
        return builder.build();
    }

    static OpenAiOfficialResponsesChatResponseMetadata buildResponseMetadata(
            String responseId,
            String modelName,
            com.openai.models.responses.Response response,
            String finishReason,
            OpenAiOfficialTokenUsage tokenUsage) {
        var builder = OpenAiOfficialResponsesChatResponseMetadata.builder()
                .id(responseId)
                .modelName(modelName)
                .createdAt((long) response.createdAt())
                .rawResponse(response);
        response.completedAt().ifPresent(ts -> builder.completedAt(ts.longValue()));
        response.serviceTier().ifPresent(tier -> builder.serviceTier(tier.asString()));
        if (finishReason != null) {
            builder.finishReason(FinishReason.valueOf(finishReason));
        }
        if (tokenUsage != null) {
            builder.tokenUsage(tokenUsage);
        }
        return builder.build();
    }

    static String mapStatusToFinishReason(String status, boolean hasToolCalls) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "completed" -> hasToolCalls ? "TOOL_EXECUTION" : "STOP";
            case "incomplete" -> "LENGTH";
            case "failed" -> "OTHER";
            default -> "OTHER";
        };
    }

    static OpenAiOfficialTokenUsage extractTokenUsage(com.openai.models.responses.Response response) {
        return response.usage()
                .map(usage -> OpenAiOfficialTokenUsage.builder()
                        .inputTokenCount(usage.inputTokens())
                        .outputTokenCount(usage.outputTokens())
                        .totalTokenCount(usage.totalTokens())
                        .inputTokensDetails(OpenAiOfficialTokenUsage.InputTokensDetails.builder()
                                .cachedTokens(usage.inputTokensDetails().cachedTokens())
                                .build())
                        .outputTokensDetails(OpenAiOfficialTokenUsage.OutputTokensDetails.builder()
                                .reasoningTokens(usage.outputTokensDetails().reasoningTokens())
                                .build())
                        .build())
                .orElse(null);
    }

    static ResponseCreateParams buildRequestParams(
            ChatRequest chatRequest, OpenAiOfficialResponsesChatRequestParameters parameters) {
        var paramsBuilder = ResponseCreateParams.builder()
                .model(ResponsesModel.ofChat(ChatModel.of(parameters.modelName())))
                .store(parameters.store());

        var inputItems = new ArrayList<ResponseInputItem>();
        for (var msg : chatRequest.messages()) {
            inputItems.addAll(toResponseInputItems(msg));
        }
        paramsBuilder.inputOfResponse(inputItems);

        if (parameters.temperature() != null) {
            paramsBuilder.temperature(parameters.temperature());
        }
        if (parameters.topP() != null) {
            paramsBuilder.topP(parameters.topP());
        }
        if (parameters.maxOutputTokens() != null) {
            paramsBuilder.maxOutputTokens(parameters.maxOutputTokens().longValue());
        }
        if (parameters.maxToolCalls() != null) {
            paramsBuilder.maxToolCalls(parameters.maxToolCalls());
        }
        if (parameters.parallelToolCalls() != null) {
            paramsBuilder.parallelToolCalls(parameters.parallelToolCalls());
        }
        if (parameters.previousResponseId() != null) {
            paramsBuilder.previousResponseId(parameters.previousResponseId());
        }
        if (parameters.topLogprobs() != null) {
            paramsBuilder.topLogprobs(parameters.topLogprobs());
        }
        if (parameters.truncation() != null) {
            paramsBuilder.truncation(ResponseCreateParams.Truncation.of(parameters.truncation()));
        }
        if (parameters.include() != null && !parameters.include().isEmpty()) {
            var includables = new ArrayList<ResponseIncludable>();
            for (var item : parameters.include()) {
                includables.add(ResponseIncludable.of(item));
            }
            paramsBuilder.include(includables);
        }
        if (parameters.serviceTier() != null) {
            paramsBuilder.serviceTier(ResponseCreateParams.ServiceTier.of(parameters.serviceTier()));
        }
        if (parameters.safetyIdentifier() != null) {
            paramsBuilder.safetyIdentifier(parameters.safetyIdentifier());
        }
        if (parameters.promptCacheKey() != null) {
            paramsBuilder.promptCacheKey(parameters.promptCacheKey());
        }
        if (parameters.promptCacheRetention() != null) {
            paramsBuilder.putAdditionalBodyProperty(
                    PROMPT_CACHE_RETENTION_FIELD, JsonValue.from(parameters.promptCacheRetention()));
        }
        if (parameters.reasoningEffort() != null || parameters.reasoningSummary() != null) {
            Reasoning.Builder reasoningBuilder = Reasoning.builder();
            if (parameters.reasoningEffort() != null) {
                reasoningBuilder.effort(parameters.reasoningEffort());
            }
            if (parameters.reasoningSummary() != null) {
                reasoningBuilder.summary(parameters.reasoningSummary());
            }
            paramsBuilder.reasoning(reasoningBuilder.build());
        }
        if (parameters.streamIncludeObfuscation() != null) {
            paramsBuilder.streamOptions(ResponseCreateParams.StreamOptions.builder()
                    .includeObfuscation(parameters.streamIncludeObfuscation())
                    .build());
        }

        boolean strictTools = Boolean.TRUE.equals(parameters.strictTools());
        List<Tool> tools = toResponsesTools(parameters.toolSpecifications(), strictTools, parameters.serverTools());
        if (!tools.isEmpty()) {
            for (Tool tool : tools) {
                paramsBuilder.addTool(tool);
            }

            if (parameters.toolChoice() != null) {
                paramsBuilder.toolChoice(toResponsesToolChoice(parameters.toolChoice()));
            }
        }

        boolean strictJsonSchema = Boolean.TRUE.equals(parameters.strictJsonSchema());
        ResponseTextConfig textConfig =
                toResponseTextConfig(parameters.responseFormat(), strictJsonSchema, parameters.textVerbosity());
        if (textConfig != null) {
            paramsBuilder.text(textConfig);
        }

        return paramsBuilder.build();
    }

    static void validate(ChatRequestParameters parameters) {
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException("'topK' parameter is not supported by OpenAI Responses API");
        }
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(
                    "'frequencyPenalty' parameter is not supported by OpenAI Responses API");
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(
                    "'presencePenalty' parameter is not supported by OpenAI Responses API");
        }
        if (parameters.stopSequences() != null && !parameters.stopSequences().isEmpty()) {
            throw new UnsupportedFeatureException("'stopSequences' parameter is not supported by OpenAI Responses API");
        }
    }

    private static List<ResponseInputItem> toResponseInputItems(ChatMessage msg) {
        if (msg instanceof SystemMessage systemMessage) {
            return List.of(createTextMessage(EasyInputMessage.Role.SYSTEM, systemMessage.text()));
        } else if (msg instanceof UserMessage userMessage) {
            return List.of(createUserMessage(userMessage));
        } else if (msg instanceof AiMessage aiMessage) {
            var items = new ArrayList<ResponseInputItem>();

            // Add reasoning item (with encrypted_content and summary) if present
            String encryptedReasoning = aiMessage.attribute(ENCRYPTED_REASONING_KEY, String.class);
            if (encryptedReasoning != null && !encryptedReasoning.isEmpty()) {
                items.add(toReasoningInputItem(encryptedReasoning, aiMessage.thinking()));
            }

            // Add text message if present
            var text = aiMessage.text();
            if (text != null && !text.isEmpty()) {
                items.add(createTextMessage(EasyInputMessage.Role.ASSISTANT, text));
            }

            // Add function calls if present
            if (aiMessage.hasToolExecutionRequests()) {
                aiMessage.toolExecutionRequests().stream()
                        .map(toolRequest -> ResponseInputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
                                .callId(toolRequest.id())
                                .name(toolRequest.name())
                                .arguments(toolRequest.arguments())
                                .build()))
                        .forEach(items::add);
            }

            // If no text and no tool calls, return empty assistant message
            if (items.isEmpty()) {
                items.add(createTextMessage(EasyInputMessage.Role.ASSISTANT, ""));
            }

            return items;
        } else if (msg instanceof ToolExecutionResultMessage toolResultMessage) {
            var outputBuilder = ResponseInputItem.FunctionCallOutput.builder().callId(toolResultMessage.id());

            if (toolResultMessage.hasSingleText()) {
                outputBuilder.output(toolResultMessage.text());
            } else {
                var outputItems = new ArrayList<ResponseFunctionCallOutputItem>();
                for (Content content : toolResultMessage.contents()) {
                    if (content instanceof TextContent textContent) {
                        outputItems.add(ResponseFunctionCallOutputItem.ofInputText(ResponseInputTextContent.builder()
                                .text(textContent.text())
                                .build()));
                    } else if (content instanceof ImageContent imageContent) {
                        outputItems.add(ResponseFunctionCallOutputItem.ofInputImage(ResponseInputImageContent.builder()
                                .imageUrl(buildImageUrl(imageContent.image()))
                                .detail(toResponsesImageDetail(imageContent.detailLevel()))
                                .build()));
                    } else {
                        throw new UnsupportedFeatureException("Unsupported content type in tool result: "
                                + content.getClass().getName()
                                + ". Only TextContent and ImageContent are supported.");
                    }
                }
                outputBuilder.output(
                        ResponseInputItem.FunctionCallOutput.Output.ofResponseFunctionCallOutputItemList(outputItems));
            }

            return List.of(ResponseInputItem.ofFunctionCallOutput(outputBuilder.build()));
        } else {
            return List.of(createTextMessage(EasyInputMessage.Role.USER, msg.toString()));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseInputItem toReasoningInputItem(String encryptedContent, String thinking) {
        List<ResponseReasoningItem.Summary> summaries = new ArrayList<>();
        if (thinking != null && !thinking.isEmpty()) {
            summaries.add(ResponseReasoningItem.Summary.builder().text(thinking).build());
        }
        ResponseReasoningItem reasoningItem = ResponseReasoningItem.builder()
                .id((JsonField) JsonMissing.of())
                .summary(summaries)
                .encryptedContent(encryptedContent)
                .build();
        return ResponseInputItem.ofReasoning(reasoningItem);
    }

    private static ResponseInputItem createTextMessage(EasyInputMessage.Role role, String text) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(EasyInputMessage.Content.ofTextInput(text))
                .build());
    }

    private static ResponseInputItem createUserMessage(UserMessage userMessage) {
        List<Content> contents = userMessage.contents();
        var contentList = new ArrayList<ResponseInputContent>();

        for (Content content : contents) {
            if (content instanceof TextContent textContent) {
                contentList.add(ResponseInputContent.ofInputText(
                        ResponseInputText.builder().text(textContent.text()).build()));
            } else if (content instanceof ImageContent imageContent) {
                Image image = imageContent.image();
                String imageUrl = buildImageUrl(image);
                contentList.add(ResponseInputContent.ofInputImage(ResponseInputImage.builder()
                        .imageUrl(imageUrl)
                        .detail(toResponsesUserImageDetail(imageContent.detailLevel()))
                        .build()));
            } else if (content instanceof PdfFileContent pdfFileContent) {
                ResponseInputFile.Builder pdfInput = ResponseInputFile.builder();
                if (pdfFileContent.pdfFile().url() != null) {
                    pdfInput.fileUrl(pdfFileContent.pdfFile().url().toString());
                } else if (pdfFileContent.pdfFile().base64Data() != null) {
                    pdfInput.filename("pdf_file");
                    pdfInput.fileData("data:" + pdfFileContent.pdfFile().mimeType() + ";base64,"
                            + pdfFileContent.pdfFile().base64Data());
                } else {
                    throw new IllegalArgumentException("PDF must have either url or base64Data");
                }
                contentList.add(ResponseInputContent.ofInputFile(pdfInput.build()));
            }
        }

        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER)
                .content(EasyInputMessage.Content.ofResponseInputMessageContentList(contentList))
                .build());
    }

    private static String buildImageUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        } else if (image.base64Data() != null) {
            String mimeType = image.mimeType() != null ? image.mimeType() : "image/jpeg";
            return "data:" + mimeType + ";base64," + image.base64Data();
        } else {
            throw new IllegalArgumentException("Image must have either url or base64Data");
        }
    }

    private static ResponseInputImage.Detail toResponsesUserImageDetail(ImageContent.DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> ResponseInputImage.Detail.LOW;
            case HIGH -> ResponseInputImage.Detail.HIGH;
            case AUTO -> ResponseInputImage.Detail.AUTO;
            default ->
                throw new UnsupportedFeatureException("DetailLevel " + detailLevel
                        + " is not supported by OpenAI Responses API. Supported values: LOW, HIGH, AUTO");
        };
    }

    private static ResponseInputImageContent.Detail toResponsesImageDetail(ImageContent.DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> ResponseInputImageContent.Detail.LOW;
            case HIGH -> ResponseInputImageContent.Detail.HIGH;
            case AUTO -> ResponseInputImageContent.Detail.AUTO;
            default ->
                throw new UnsupportedFeatureException("DetailLevel " + detailLevel
                        + " is not supported by OpenAI Responses API. Supported values: LOW, HIGH, AUTO");
        };
    }

    private static FunctionTool toResponsesTool(ToolSpecification toolSpec, boolean strict) {
        boolean effectiveStrict = ToolSpecificationUtils.isEffectivelyStrict(toolSpec, strict);
        try {
            var parametersBuilder = FunctionTool.Parameters.builder();
            if (toolSpec.parameters() != null) {
                toMap(toolSpec.parameters(), effectiveStrict)
                        .forEach((key, value) -> parametersBuilder.putAdditionalProperty(key, JsonValue.from(value)));
            } else if (effectiveStrict) {
                parametersBuilder
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(Collections.emptyMap()))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false));
            }
            return FunctionTool.builder()
                    .name(toolSpec.name())
                    .description(toolSpec.description())
                    .parameters(parametersBuilder.build())
                    .strict(effectiveStrict)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert tool specification for tool: " + toolSpec.name(), e);
        }
    }

    private static List<Tool> toResponsesTools(
            List<ToolSpecification> toolSpecifications, boolean strict, List<Tool> serverTools) {
        List<Tool> tools = new ArrayList<>();
        if (toolSpecifications != null) {
            for (ToolSpecification toolSpecification : toolSpecifications) {
                tools.add(Tool.ofFunction(toResponsesTool(toolSpecification, strict)));
            }
        }
        if (serverTools != null) {
            tools.addAll(serverTools);
        }
        return tools;
    }

    private static ToolChoiceOptions toResponsesToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        return switch (toolChoice) {
            case AUTO -> ToolChoiceOptions.AUTO;
            case REQUIRED -> ToolChoiceOptions.REQUIRED;
            case NONE -> ToolChoiceOptions.NONE;
        };
    }

    private static ResponseTextConfig toResponseTextConfig(
            ResponseFormat responseFormat, Boolean strict, String textVerbosity) {
        ResponseTextConfig.Builder builder = null;

        if (responseFormat != null && responseFormat.type() != ResponseFormatType.TEXT) {
            builder = ResponseTextConfig.builder();
            JsonSchema jsonSchema = responseFormat.jsonSchema();
            if (jsonSchema == null) {
                builder.format(ResponseFormatTextConfig.ofJsonObject(
                        ResponseFormatJsonObject.builder().build()));
            } else {
                if (!(jsonSchema.rootElement() instanceof JsonObjectSchema
                        || jsonSchema.rootElement() instanceof JsonRawSchema)) {
                    throw new IllegalArgumentException(
                            "For OpenAI, the root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                                    + jsonSchema.rootElement().getClass());
                }

                Map<String, Object> schemaMap = toMap(jsonSchema.rootElement(), strict);
                ResponseFormatTextJsonSchemaConfig.Schema.Builder schemaBuilder =
                        ResponseFormatTextJsonSchemaConfig.Schema.builder();

                for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
                    schemaBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
                }

                ResponseFormatTextJsonSchemaConfig schemaConfig = ResponseFormatTextJsonSchemaConfig.builder()
                        .name(jsonSchema.name())
                        .schema(schemaBuilder.build())
                        .strict(strict)
                        .build();

                builder.format(ResponseFormatTextConfig.ofJsonSchema(schemaConfig));
            }
        }

        if (textVerbosity != null && !textVerbosity.isEmpty()) {
            if (builder == null) {
                builder = ResponseTextConfig.builder();
            }
            builder.verbosity(com.openai.models.responses.ResponseTextConfig.Verbosity.Companion.of(textVerbosity));
        }

        return builder != null ? builder.build() : null;
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Credential credential;
        private String microsoftFoundryDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private boolean isMicrosoftFoundry;
        private boolean isGitHubModels;
        private Map<String, String> customHeaders;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;

        private OpenAIClient client;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private Integer maxToolCalls;
        private Boolean parallelToolCalls;
        private String previousResponseId;
        private Integer topLogprobs;
        private String truncation;
        private List<String> include;
        private String serviceTier;
        private String safetyIdentifier;
        private String promptCacheKey;
        private String promptCacheRetention;
        private ReasoningEffort reasoningEffort;
        private Reasoning.Summary reasoningSummary;
        private String textVerbosity;
        private Boolean streamIncludeObfuscation;
        private Boolean store;
        private List<ChatModelListener> listeners;
        private ExecutorService executorService;
        private Boolean strictTools;
        private Boolean strictJsonSchema;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;
        private ChatRequestParameters defaultRequestParameters;
        private List<Tool> serverTools;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        /**
         * @deprecated Use {@link #microsoftFoundryDeploymentName(String)} instead
         */
        @Deprecated
        public Builder azureDeploymentName(String azureDeploymentName) {
            this.microsoftFoundryDeploymentName = azureDeploymentName;
            return this;
        }

        public Builder microsoftFoundryDeploymentName(String microsoftFoundryDeploymentName) {
            this.microsoftFoundryDeploymentName = microsoftFoundryDeploymentName;
            return this;
        }

        public Builder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * @deprecated Use {@link #isMicrosoftFoundry(boolean)} instead
         */
        @Deprecated
        public Builder isAzure(boolean isAzure) {
            this.isMicrosoftFoundry = isAzure;
            return this;
        }

        public Builder isMicrosoftFoundry(boolean isMicrosoftFoundry) {
            this.isMicrosoftFoundry = isMicrosoftFoundry;
            return this;
        }

        public Builder isGitHubModels(boolean isGitHubModels) {
            this.isGitHubModels = isGitHubModels;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder client(OpenAIClient client) {
            this.client = client;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder maxToolCalls(Integer maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder truncation(String truncation) {
            this.truncation = truncation;
            return this;
        }

        public Builder include(List<String> include) {
            this.include = include;
            return this;
        }

        /**
         * When Enterprise Open AI subscription is used, service tier = "priority" puts requests into a
         * faster pool.
         */
        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder safetyIdentifier(String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        public Builder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        public Builder promptCacheRetention(String promptCacheRetention) {
            this.promptCacheRetention = promptCacheRetention;
            return this;
        }

        public Builder reasoningEffort(ReasoningEffort reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        /**
         * @deprecated use {@link #reasoningEffort(ReasoningEffort)} instead
         */
        @Deprecated(since = "1.14.0")
        public Builder reasoningEffort(String reasoningEffort) {
            return reasoningEffort(reasoningEffort != null ? ReasoningEffort.of(reasoningEffort) : null);
        }

        public Builder reasoningSummary(Reasoning.Summary reasoningSummary) {
            this.reasoningSummary = reasoningSummary;
            return this;
        }

        public Builder textVerbosity(String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        public Builder streamIncludeObfuscation(Boolean streamIncludeObfuscation) {
            this.streamIncludeObfuscation = streamIncludeObfuscation;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder listeners(ChatModelListener... listeners) {
            return this.listeners(asList(listeners));
        }

        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * @deprecated use {@link #strictTools(Boolean)} and {@link #strictJsonSchema(Boolean)} instead
         */
        @Deprecated(since = "1.13.0")
        public Builder strict(Boolean strict) {
            this.strictTools = strict;
            this.strictJsonSchema = strict;
            return this;
        }

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public Builder serverTools(List<Tool> serverTools) {
            this.serverTools = serverTools;
            return this;
        }

        public Builder serverTools(Tool... serverTools) {
            return serverTools(asList(serverTools));
        }

        public OpenAiOfficialResponsesStreamingChatModel build() {
            return new OpenAiOfficialResponsesStreamingChatModel(this);
        }
    }

    /**
     * Event handler for Responses API streaming.
     */
    private static class ResponsesEventHandler {

        private final StreamingChatResponseHandler handler;
        private final AtomicReference<String> responseIdRef;
        private final String modelName;
        private final StreamingHandle streamingHandle;
        private final Map<String, ToolExecutionRequest.Builder> toolCallBuilders = new HashMap<>();
        private final Map<String, Integer> toolCallIndices = new HashMap<>();
        private final List<ToolExecutionRequest> completedToolCalls = new ArrayList<>();
        private final StringBuilder textBuilder = new StringBuilder();
        private OpenAiOfficialTokenUsage tokenUsage;
        private String responseId;
        private String finishReason;
        private int nextToolCallIndex = 0;

        ResponsesEventHandler(
                StreamingChatResponseHandler handler,
                AtomicReference<String> responseIdRef,
                String modelName,
                StreamingHandle streamingHandle) {
            this.handler = handler;
            this.responseIdRef = responseIdRef;
            this.modelName = modelName;
            this.streamingHandle = streamingHandle;
        }

        void handleEvent(ResponseStreamEvent event) {
            if (streamingHandle != null && streamingHandle.isCancelled()) {
                throw new CancellationException("Request cancelled by user");
            }

            try {
                if (event.isCreated()) {
                    handleCreated(event.asCreated());
                } else if (event.isOutputTextDelta()) {
                    handleOutputTextDelta(event.asOutputTextDelta());
                } else if (event.isOutputItemAdded()) {
                    handleOutputItemAdded(event.asOutputItemAdded());
                } else if (event.isReasoningTextDelta()) {
                    handleReasoningTextDelta(event.asReasoningTextDelta());
                } else if (event.isReasoningSummaryTextDelta()) {
                    handleReasoningSummaryTextDelta(event.asReasoningSummaryTextDelta());
                } else if (event.isFunctionCallArgumentsDelta()) {
                    handleFunctionCallArgumentsDelta(event.asFunctionCallArgumentsDelta());
                } else if (event.isFunctionCallArgumentsDone()) {
                    handleFunctionCallArgumentsDone(event.asFunctionCallArgumentsDone());
                } else if (event.isOutputItemDone()) {
                    handleOutputItemDone(event.asOutputItemDone());
                } else if (event.isCompleted()) {
                    handleCompleted(event.asCompleted());
                } else if (event.isError()) {
                    handleError(event.asError());
                } else if (event.isFailed()) {
                    handleFailed(event.asFailed());
                } else if (event.isIncomplete()) {
                    handleIncomplete(event.asIncomplete());
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void handleCreated(ResponseCreatedEvent event) {
            this.responseId = event.response().id();
            responseIdRef.set(responseId);
        }

        private void handleOutputTextDelta(ResponseTextDeltaEvent event) {
            var delta = event.delta();
            if (!delta.isEmpty()) {
                textBuilder.append(delta);
                onPartialResponse(handler, delta, streamingHandle);
            }
        }

        private void handleOutputItemAdded(ResponseOutputItemAddedEvent event) {
            var item = event.item();
            if (item.isFunctionCall()) {
                var functionCall = item.asFunctionCall();
                var itemId = functionCall.id().orElse(null);
                if (itemId != null) {
                    toolCallBuilders.put(
                            itemId,
                            ToolExecutionRequest.builder()
                                    .id(functionCall.callId())
                                    .name(functionCall.name())
                                    .arguments(""));
                    toolCallIndices.put(itemId, nextToolCallIndex++);
                } else {
                    logger.warn("Function call missing item ID: {}", functionCall.callId());
                }
            }
        }

        private void handleFunctionCallArgumentsDelta(ResponseFunctionCallArgumentsDeltaEvent event) {
            String itemId = event.itemId();
            var builder = toolCallBuilders.get(itemId);
            var index = toolCallIndices.get(itemId);
            if (builder == null || index == null) {
                return;
            }

            String delta = event.delta();
            if (delta.isEmpty()) {
                return;
            }

            String currentArgs = builder.build().arguments();
            builder.arguments(currentArgs + delta);

            PartialToolCall partialToolCall = PartialToolCall.builder()
                    .index(index)
                    .id(builder.build().id())
                    .name(builder.build().name())
                    .partialArguments(delta)
                    .build();
            onPartialToolCall(handler, partialToolCall, streamingHandle);
        }

        private void handleReasoningTextDelta(ResponseReasoningTextDeltaEvent event) {
            String delta = event.delta();
            if (!delta.isEmpty()) {
                onPartialThinking(handler, delta, streamingHandle);
            }
        }

        private void handleReasoningSummaryTextDelta(ResponseReasoningSummaryTextDeltaEvent event) {
            String delta = event.delta();
            if (!delta.isEmpty()) {
                onPartialThinking(handler, delta, streamingHandle);
            }
        }

        private void handleFunctionCallArgumentsDone(ResponseFunctionCallArgumentsDoneEvent event) {
            var itemId = event.itemId();
            var builder = toolCallBuilders.remove(itemId);
            var index = toolCallIndices.remove(itemId);
            if (builder != null && index != null) {
                builder.arguments(event.arguments());
                ToolExecutionRequest toolExecutionRequest = builder.build();
                completedToolCalls.add(toolExecutionRequest);

                if (!streamingHandle.isCancelled()) {
                    try {
                        handler.onCompleteToolCall(new CompleteToolCall(index, toolExecutionRequest));
                    } catch (Exception e) {
                        withLoggingExceptions(() -> handler.onError(e));
                    }
                }
            } else {
                logger.warn("No builder for itemId in argumentsDone: {}", itemId);
            }
        }

        private void handleOutputItemDone(ResponseOutputItemDoneEvent event) {
            // No-op
        }

        private void handleCompleted(ResponseCompletedEvent event) {
            var response = event.response();

            // Extract status and map to finish reason
            response.status().ifPresent(status -> {
                this.finishReason = mapStatusToFinishReason(status.asString(), !completedToolCalls.isEmpty());
            });

            // Extract token usage and complete
            extractTokenUsageAndComplete(response);
        }

        private void handleError(ResponseErrorEvent event) {
            var message = event.message();
            withLoggingExceptions(() -> handler.onError(new RuntimeException("Response error: " + message)));
        }

        private void handleFailed(ResponseFailedEvent event) {
            var response = event.response();
            var message = response.error().map(this::extractErrorMessage).orElse("Response failed");
            withLoggingExceptions(() -> handler.onError(new RuntimeException("Response failed: " + message)));
        }

        private String extractErrorMessage(ResponseError error) {
            var message = error.message();
            if (message.isBlank()) {
                return error.toString();
            }
            return message;
        }

        private void handleIncomplete(ResponseIncompleteEvent event) {
            // Incomplete is not an error - it just means the response was cut off due to token limits
            // Treat it as a normal completion with finish reason LENGTH
            finishReason = "LENGTH";

            // Complete the response normally
            extractTokenUsageAndComplete(event.response());
        }

        private void extractTokenUsageAndComplete(com.openai.models.responses.Response response) {
            var text = !textBuilder.isEmpty() ? textBuilder.toString() : null;
            var aiMessage = buildAiMessage(
                    text, extractReasoningSummary(response), completedToolCalls, extractEncryptedReasoning(response));

            tokenUsage = extractTokenUsage(response);
            var metadata = buildResponseMetadata(responseId, modelName, response, finishReason, tokenUsage);

            var chatResponse = ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(metadata)
                    .build();

            if (!streamingHandle.isCancelled()) {
                try {
                    handler.onCompleteResponse(chatResponse);
                } catch (Exception e) {
                    withLoggingExceptions(() -> handler.onError(e));
                }
            }
        }
    }

    private static class ResponsesStreamingHandle implements StreamingHandle {

        private final Runnable cancelCallback;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Future<?> streamingFuture;
        private volatile boolean completed;

        ResponsesStreamingHandle(Runnable cancelCallback) {
            this.cancelCallback = cancelCallback;
        }

        void setStreamingFuture(Future<?> streamingFuture) {
            this.streamingFuture = streamingFuture;
            if (cancelled.get() && streamingFuture != null) {
                streamingFuture.cancel(true);
            }
        }

        void markCompleted() {
            this.completed = true;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                if (!completed && cancelCallback != null) {
                    try {
                        cancelCallback.run();
                    } catch (Exception ignored) {
                    }
                }
                if (streamingFuture != null) {
                    streamingFuture.cancel(true);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
