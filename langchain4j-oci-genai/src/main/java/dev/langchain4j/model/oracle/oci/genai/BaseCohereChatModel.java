package dev.langchain4j.model.oracle.oci.genai;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import com.oracle.bmc.generativeaiinference.model.CohereChatBotMessage;
import com.oracle.bmc.generativeaiinference.model.CohereChatRequest;
import com.oracle.bmc.generativeaiinference.model.CohereChatResponse;
import com.oracle.bmc.generativeaiinference.model.CohereMessage;
import com.oracle.bmc.generativeaiinference.model.CohereParameterDefinition;
import com.oracle.bmc.generativeaiinference.model.CohereResponseFormat;
import com.oracle.bmc.generativeaiinference.model.CohereResponseJsonFormat;
import com.oracle.bmc.generativeaiinference.model.CohereResponseTextFormat;
import com.oracle.bmc.generativeaiinference.model.CohereSystemMessage;
import com.oracle.bmc.generativeaiinference.model.CohereTool;
import com.oracle.bmc.generativeaiinference.model.CohereToolCall;
import com.oracle.bmc.generativeaiinference.model.CohereToolResult;
import com.oracle.bmc.generativeaiinference.model.CohereUserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class BaseCohereChatModel<T extends BaseCohereChatModel<T>> extends BaseChatModel<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseCohereChatModel.class);

    private final Builder<?, ?> builder;

    BaseCohereChatModel(Builder<?, ?> builder) {
        super(builder);
        this.builder = builder;
    }

    /**
     * Maps lc4j chat request and sets configured properties.
     *
     * @param chatRequest lc4j chat request
     * @return OCI BMC generic chat request
     */
    protected CohereChatRequest.Builder prepareRequest(ChatRequest chatRequest) {
        var requestBuilder = map(chatRequest);

        // Common for Generic and Cohere
        setIfNotNull(builder.maxTokens(), requestBuilder::maxTokens);
        setIfNotNull(builder.topK(), requestBuilder::topK);
        setIfNotNull(builder.topP(), requestBuilder::topP);
        setIfNotNull(builder.temperature(), requestBuilder::temperature);
        setIfNotNull(builder.frequencyPenalty(), requestBuilder::frequencyPenalty);
        setIfNotNull(builder.presencePenalty(), requestBuilder::presencePenalty);
        setIfNotNull(builder.seed(), requestBuilder::seed);
        setIfNotNull(builder.stop(), requestBuilder::stopSequences);

        // Cohere specific
        setIfNotNull(builder.documents(), requestBuilder::documents);
        setIfNotNull(builder.isSearchQueriesOnly(), requestBuilder::isSearchQueriesOnly);
        setIfNotNull(builder.preambleOverride(), requestBuilder::preambleOverride);
        setIfNotNull(builder.maxInputTokens(), requestBuilder::maxInputTokens);
        setIfNotNull(builder.promptTruncation(), requestBuilder::promptTruncation);
        setIfNotNull(builder.isRawPrompting(), requestBuilder::isRawPrompting);
        setIfNotNull(builder.citationQuality(), requestBuilder::citationQuality);

        // Per-request overrides
        var params = chatRequest.parameters();
        setIfNotNull(params.maxOutputTokens(), requestBuilder::maxTokens);
        setIfNotNull(params.topK(), requestBuilder::topK);
        setIfNotNull(params.topP(), requestBuilder::topP);
        setIfNotNull(params.temperature(), requestBuilder::temperature);
        setIfNotNull(params.frequencyPenalty(), requestBuilder::frequencyPenalty);
        setIfNotNull(params.presencePenalty(), requestBuilder::presencePenalty);
        setIfNotNull(params.stopSequences(), requestBuilder::stopSequences);

        return requestBuilder;
    }

    static CohereChatRequest.Builder map(ChatRequest chatRequest) {

        var builder = CohereChatRequest.builder();

        var toolResults = new ArrayList<CohereToolResult>();
        var chatHistory = new ArrayList<CohereMessage>();
        CohereUserMessage firstUserMessage = null;

        for (ChatMessage chatMessage : chatRequest.messages()) {
            switch (chatMessage.type()) {
                case USER -> {
                    var userMessage = (dev.langchain4j.data.message.UserMessage) chatMessage;

                    var cohereUserMessage = CohereUserMessage.builder()
                            .message(userMessage.singleText())
                            .build();
                    if (firstUserMessage == null) {
                        firstUserMessage = cohereUserMessage;
                    }
                    chatHistory.add(cohereUserMessage);
                }
                case TOOL_EXECUTION_RESULT -> {
                    var toolResultMessage = (dev.langchain4j.data.message.ToolExecutionResultMessage) chatMessage;
                    toolResults.add(CohereToolResult.builder()
                            .call(CohereToolCall.builder()
                                    .name(toolResultMessage.toolName())
                                    .build())
                            // https://docs.cohere.com/v1/reference/chat
                            // [{<key>: <value>}]
                            .outputs(List.of(Map.of("result", toolResultMessage.text())))
                            .build());
                }
                case SYSTEM -> {
                    var systemMessage = (dev.langchain4j.data.message.SystemMessage) chatMessage;
                    chatHistory.add(CohereSystemMessage.builder()
                            .message(systemMessage.text())
                            .build());
                }
                case AI -> {
                    var aiMessage = (dev.langchain4j.data.message.AiMessage) chatMessage;

                    var assistantMessageBuilder = CohereChatBotMessage.builder();

                    if (aiMessage.hasToolExecutionRequests()) {
                        var toolCalls = new ArrayList<CohereToolCall>();
                        for (ToolExecutionRequest toolExecReq : aiMessage.toolExecutionRequests()) {
                            toolCalls.add(CohereToolCall.builder()
                                    .name(toolExecReq.name())
                                    .parameters(fromJson(toolExecReq.arguments(), Map.class))
                                    .build());
                        }
                        assistantMessageBuilder.toolCalls(toolCalls);
                    }
                    // https://docs.cohere.com/v1/reference/chat
                    // Chat calls with tool_results should not be included in the Chat history
                    // to avoid duplication of the message text.
                    //                        chatHistory.add(assistantMessageBuilder
                    //                                .message(aiMessage.text())
                    //                                .build());
                }
                default -> throw new UnsupportedOperationException("Unsupported message type: " + chatMessage.type());
            }
        }

        var bmcTools = Optional.ofNullable(chatRequest.toolSpecifications()).orElse(List.of()).stream()
                .map(BaseCohereChatModel::map)
                .toList();

        if (!chatHistory.isEmpty()) {
            builder.chatHistory(chatHistory);
        }
        if (!bmcTools.isEmpty()) {
            builder.tools(bmcTools);
        }

        if (!toolResults.isEmpty()) {
            builder.toolResults(toolResults);
            builder.isForceSingleStep(true);
        }

        if (firstUserMessage != null) {
            builder.message(firstUserMessage.getMessage());
        }

        builder.responseFormat(map(chatRequest.responseFormat()));
        return builder;
    }

    //    // TODO: remove
    //    @Deprecated
    //    static ChatContent map(Content lc4jContent) {
    //        return switch (lc4jContent.type()) {
    //            case TEXT -> {
    //                var textContent = (dev.langchain4j.data.message.TextContent) lc4jContent;
    //                yield TextContent.builder().text(textContent.text()).build();
    //            }
    //            case IMAGE -> {
    //                var imageContent = (dev.langchain4j.data.message.ImageContent) lc4jContent;
    //                Image image = imageContent.image();
    //                var ociImageBuilder = ImageUrl.builder();
    //                if (image.url() != null) {
    //                    ociImageBuilder.url(image.url().toString());
    //                } else {
    //                    // rfc2397
    //                    ociImageBuilder.url("data:" + image.mimeType() + ";base64," + image.base64Data());
    //                }
    //
    //                yield ImageContent.builder()
    //                        .imageUrl(ociImageBuilder
    //                                .detail(ImageUrl.Detail.create(
    //                                        imageContent.detailLevel().name()))
    //                                .build())
    //                        .build();
    //            }
    //            default -> throw new IllegalStateException("Unsupported content type: "
    //                    + lc4jContent.type()
    //                    + " only TEXT and IMAGE content types are supported.");
    //        };
    //    }

    static CohereResponseFormat map(ResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        return switch (responseFormat.type()) {
            case TEXT -> CohereResponseTextFormat.builder().build();
            case JSON -> CohereResponseJsonFormat.builder().build();
        };
    }

    static CohereTool map(ToolSpecification toolSpec) {
        var b = CohereTool.builder();

        if (toolSpec.parameters() != null) {

            final JsonObjectSchema lc4jParams = toolSpec.parameters();

            var paramMap = new HashMap<String, CohereParameterDefinition>();
            for (var entry : lc4jParams.properties().entrySet()) {
                Map<String, Object> map = JsonSchemaElementUtils.toMap(entry.getValue());
                var cohParBuilder = CohereParameterDefinition.builder();
                setIfNotNull(map.get("type"), t -> cohParBuilder.type(t.toString()));
                setIfNotNull(map.get("description"), t -> cohParBuilder.description(t.toString()));
                setIfNotNull(map.get("required"), t -> cohParBuilder.isRequired(Boolean.parseBoolean(t.toString())));
                paramMap.put(entry.getKey(), cohParBuilder.build());
            }

            b.parameterDefinitions(paramMap);
        }
        var desc = toolSpec.description();
        if (Utils.isNullOrEmpty(desc)) {
            LOGGER.warn(
                    "Description of tool '{}' is empty, cohere requires tool description, using tool name instead.",
                    toolSpec.name());
            desc = toolSpec.name();
        }
        return b.name(toolSpec.name()).description(desc).build();
    }

    static ChatResponse map(com.oracle.bmc.generativeaiinference.responses.ChatResponse response, String modelName) {
        return map((CohereChatResponse) response.getChatResult().getChatResponse(), modelName, null)
                .build();
    }

    static ChatResponse.Builder map(
            CohereChatResponse bmcResponse, String modelName, FinishReason finishReasonOverride) {
        List<CohereMessage> choices =
                Optional.ofNullable(bmcResponse.getChatHistory()).orElseGet(List::of);

        var aiMessageBuilder = AiMessage.builder();
        aiMessageBuilder.text(bmcResponse.getText());

        List<ToolExecutionRequest> executionRequests = new ArrayList<>();

        executionRequests.addAll(Optional.ofNullable(bmcResponse.getToolCalls()).orElse(List.of()).stream()
                .map(toolCall -> ToolExecutionRequest.builder()
                        .arguments(toJson(toolCall.getParameters()))
                        .name(toolCall.getName())
                        .build())
                .toList());

        aiMessageBuilder.toolExecutionRequests(executionRequests);

        var chatResponseMetadataBuilder = ChatResponseMetadata.builder();

        if (finishReasonOverride != null) {
            chatResponseMetadataBuilder.finishReason(finishReasonOverride);
        } else {
            chatResponseMetadataBuilder.finishReason(map(bmcResponse.getFinishReason()));
        }
        chatResponseMetadataBuilder.modelName(modelName);
        // TODO: Token usage is actually sent from OCI but SDK doesn't use it
        // {
        //    "chatResponse": {
        //        "apiFormat": "GENERIC",
        //        "timeCreated": "2025-04-24T14:57:17.740Z",
        //        "choices": [
        //            "..."
        //        ],
        //        "usage": {
        //            "completionTokens": 13,
        //            "promptTokens": 233,
        //            "totalTokens": 246
        //        }
        //    }
        // }
        chatResponseMetadataBuilder.tokenUsage(new TokenUsage(1, 1, 1));

        return ChatResponse.builder()
                .metadata(chatResponseMetadataBuilder.build())
                .aiMessage(aiMessageBuilder.build());
    }

    static FinishReason map(CohereChatResponse.FinishReason finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason) {
            case Complete -> TOOL_EXECUTION;
            case ErrorLimit -> LENGTH;
            case Error -> TOOL_EXECUTION;
            case MaxTokens -> TOOL_EXECUTION;
            default -> null;
        };
    }

    abstract static class Builder<T extends BaseCohereChatModel<T>, B extends Builder<T, B>>
            extends BaseChatModel.Builder<T, B> {

        private List<Object> documents;
        private Boolean isSearchQueriesOnly;
        private String preambleOverride;
        private Integer maxInputTokens;
        private CohereChatRequest.PromptTruncation promptTruncation;
        private Boolean isRawPrompting;
        private CohereChatRequest.CitationQuality citationQuality;

        protected Builder() {}

        /**
         * A list of relevant documents that the model can refer to for generating grounded
         * responses to the user's requests. Some example keys that you can add to the dictionary
         * are "text", "author", and "date". Keep the total word count of the strings in the
         * dictionary to 300 words or fewer.
         *
         * <p>Example: {@code [ { "title": "Tall penguins", "snippet": "Emperor penguins are the
         * tallest." }, { "title": "Penguin habitats", "snippet": "Emperor penguins only live in
         * Antarctica." } ]}
         *
         * @param documents Value to set
         * @return builder
         */
        public B documents(List<Object> documents) {
            this.documents = documents;
            return self();
        }

        List<Object> documents() {
            return documents;
        }

        /**
         * When set to true, the response contains only a list of generated search queries without
         * the search results and the model will not respond to the user's message.
         *
         * @param isSearchQueriesOnly Value to set
         * @return builder
         */
        public B isSearchQueriesOnly(Boolean isSearchQueriesOnly) {
            this.isSearchQueriesOnly = isSearchQueriesOnly;
            return self();
        }

        Boolean isSearchQueriesOnly() {
            return isSearchQueriesOnly;
        }

        /**
         * If specified, the default Cohere preamble is replaced with the provided preamble. A
         * preamble is an initial guideline message that can change the model's overall chat
         * behavior and conversation style. Default preambles vary for different models.
         *
         * <p>Example: {@code You are a travel advisor. Answer with a pirate tone.}
         *
         * @param preambleOverride Value to set
         * @return builder
         */
        public B preambleOverride(String preambleOverride) {
            this.preambleOverride = preambleOverride;
            return self();
        }

        String preambleOverride() {
            return preambleOverride;
        }

        /**
         * The maximum number of input tokens to send to the model. If not specified,
         * max_input_tokens is the model's context length limit minus a small buffer.
         *
         * @param maxInputTokens Value to set
         * @return builder
         */
        public B maxInputTokens(Integer maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
            return self();
        }

        Integer maxInputTokens() {
            return maxInputTokens;
        }

        /**
         * Defaults to OFF. Dictates how the prompt will be constructed. With {@code
         * promptTruncation} set to AUTO_PRESERVE_ORDER, some elements from {@code chatHistory} and
         * {@code documents} will be dropped to construct a prompt that fits within the model's
         * context length limit. During this process the order of the documents and chat history
         * will be preserved. With {@code prompt_truncation} set to OFF, no elements will be
         * dropped.
         *
         * @param promptTruncation Value to set
         * @return builder
         */
        public B promptTruncation(CohereChatRequest.PromptTruncation promptTruncation) {
            this.promptTruncation = promptTruncation;
            return self();
        }

        CohereChatRequest.PromptTruncation promptTruncation() {
            return promptTruncation;
        }

        /**
         * When enabled, the user's {@code message} will be sent to the model without any
         * preprocessing.
         *
         * @param isRawPrompting Value to set
         * @return builder
         */
        public B isRawPrompting(Boolean isRawPrompting) {
            this.isRawPrompting = isRawPrompting;
            return self();
        }

        Boolean isRawPrompting() {
            return isRawPrompting;
        }

        /**
         * When FAST is selected, citations are generated at the same time as the text output and
         * the request will be completed sooner. May result in less accurate citations.
         *
         * @param citationQuality Value to set
         * @return builder
         */
        public B citationQuality(CohereChatRequest.CitationQuality citationQuality) {
            this.citationQuality = citationQuality;
            return self();
        }

        CohereChatRequest.CitationQuality citationQuality() {
            return citationQuality;
        }

        /**
         * Build new instance.
         *
         * @return the instance
         */
        public abstract T build();
    }
}
