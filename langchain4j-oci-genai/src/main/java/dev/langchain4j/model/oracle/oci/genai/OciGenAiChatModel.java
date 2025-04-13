package dev.langchain4j.model.oracle.oci.genai;

import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import com.oracle.bmc.generativeaiinference.model.AssistantMessage;
import com.oracle.bmc.generativeaiinference.model.ChatChoice;
import com.oracle.bmc.generativeaiinference.model.ChatResult;
import com.oracle.bmc.generativeaiinference.model.FunctionCall;
import com.oracle.bmc.generativeaiinference.model.GenericChatResponse;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat models hosted on OCI GenAI.
 * <p>OCI Generative AI is a fully managed service that provides a set of state-of-the-art,
 * customizable large language models (LLMs) that cover a wide range of use cases for text
 * generation, summarization, and text embeddings.
 *
 * <p>For Cohere models use {@link OciGenAiCohereChatModel}.
 *
 * <p>To learn more about the service, see the <a href="https://docs.oracle.com/iaas/Content/generative-ai/home.htm">Generative AI documentation</a>
 */
public class OciGenAiChatModel extends BaseGenericChatModel<OciGenAiChatModel> implements ChatModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(OciGenAiChatModel.class);
    private final Builder builder;

    OciGenAiChatModel(Builder builder) {
        super(builder);
        this.builder = builder;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OCI_GEN_AI;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return this.builder.listeners();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder()
                .modelName(builder.chatModelId())
                .frequencyPenalty(builder.frequencyPenalty())
                .maxOutputTokens(builder.maxTokens())
                .presencePenalty(builder.presencePenalty())
                .stopSequences(builder.stop())
                .temperature(builder.temperature())
                .topK(builder.topK())
                .topP(builder.topP())
                .build();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        var b = super.prepareRequest(chatRequest);
        return map(super.ociChat(b.build()), this.builder.chatModelId());
    }

    static ChatResponse map(com.oracle.bmc.generativeaiinference.responses.ChatResponse bmcResponse, String modelName) {
        ChatResult chatResult = bmcResponse.getChatResult();
        GenericChatResponse chatResponse = (GenericChatResponse) chatResult.getChatResponse();
        List<ChatChoice> choices = chatResponse.getChoices();

        if (choices.size() != 1) {
            LOGGER.warn("Unexpected number of choices: {}", choices.size());
            LOGGER.warn("ChatResponse: {}", chatResponse);
        }

        ChatChoice choice = choices.get(0);

        return map(choice, modelName);
    }

    static ChatResponse map(ChatChoice choice, String modelName) {
        var message = choice.getMessage();

        var content = message.getContent();

        var aiMessageBuilder = AiMessage.builder();

        if (content != null) {
            aiMessageBuilder.text(content.stream()
                    .map(TextContent.class::cast)
                    .map(TextContent::getText)
                    .collect(Collectors.joining()));
        }

        if (message instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null) {
            var toolExecutionRequests = assistantMessage.getToolCalls().stream()
                    .map(FunctionCall.class::cast)
                    .map(functionCall -> ToolExecutionRequest.builder()
                            .id(functionCall.getId())
                            .arguments(functionCall.getArguments())
                            .name(functionCall.getName())
                            .build())
                    .toList();

            aiMessageBuilder.toolExecutionRequests(toolExecutionRequests);
        }

        var chatResponseMetadataBuilder = ChatResponseMetadata.builder();

        chatResponseMetadataBuilder.finishReason(parseFinishReason(choice.getFinishReason()));
        chatResponseMetadataBuilder.modelName(modelName);
        // TODO: Token usage is sent from OCI but ignored by SDK
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
                .aiMessage(aiMessageBuilder.build())
                .metadata(chatResponseMetadataBuilder.build())
                .build();
    }

    static FinishReason parseFinishReason(String finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason) {
            case "stop" -> STOP;
            case "length" -> LENGTH;
            case "tool_calls", "function_call" -> TOOL_EXECUTION;
            case "content_filter" -> CONTENT_FILTER;
            default -> throw new IllegalArgumentException("Unknown finish reason: " + finishReason);
        };
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Model builder.
     */
    public static class Builder extends BaseGenericChatModel.Builder<OciGenAiChatModel, Builder> {

        Builder() {}

        @Override
        Builder self() {
            return this;
        }

        @Override
        public OciGenAiChatModel build() {
            return new OciGenAiChatModel(this);
        }
    }
}
