package dev.langchain4j.model.mistralai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import okhttp3.Headers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

public class DefaultMistralAiHelper {

    static final String MISTRALAI_API_URL = "https://api.mistral.ai/v1";
    static final String MISTRALAI_API_CREATE_EMBEDDINGS_ENCODING_FORMAT = "float";
    private static final Pattern MISTRAI_API_KEY_BEARER_PATTERN =
            Pattern.compile("^(Bearer\\s*) ([A-Za-z0-9]{1,32})$");

    static List<MistralAiChatMessage> toMistralAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultMistralAiHelper::toMistralAiMessage)
                .collect(toList());
    }

    static MistralAiChatMessage toMistralAiMessage(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.SYSTEM)
                    .content(((SystemMessage) message).text())
                    .build();
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;

            if (!aiMessage.hasToolExecutionRequests()) {
                return MistralAiChatMessage.builder()
                        .role(MistralAiRole.ASSISTANT)
                        .content(aiMessage.text())
                        .build();
            }

            List<MistralAiToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(DefaultMistralAiHelper::toMistralAiToolCall)
                    .collect(toList());

            if (isNullOrBlank(aiMessage.text())){
                return MistralAiChatMessage.builder()
                        .role(MistralAiRole.ASSISTANT)
                        .content(null)
                        .toolCalls(toolCalls)
                        .build();
            }

            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.ASSISTANT)
                    .content(aiMessage.text())
                    .toolCalls(toolCalls)
                    .build();
        }

        if (message instanceof UserMessage) {
            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.USER)
                    .content(message.text()) // MistralAI support Text Content only as String
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage){
            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.TOOL)
                    .name(((ToolExecutionResultMessage) message).toolName())
                    .content(((ToolExecutionResultMessage) message).text())
                    .build();
        }

        throw new IllegalArgumentException("Unknown message type: " + message.type());
    }

    static MistralAiToolCall toMistralAiToolCall(ToolExecutionRequest toolExecutionRequest) {
        return MistralAiToolCall.builder()
                .id(toolExecutionRequest.id())
                .function(MistralAiFunctionCall.builder()
                        .name(toolExecutionRequest.name())
                        .arguments(toolExecutionRequest.arguments())
                        .build())
                .build();
    }

    public static TokenUsage tokenUsageFrom(MistralAiUsage mistralAiUsage) {
        if (mistralAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                mistralAiUsage.getPromptTokens(),
                mistralAiUsage.getCompletionTokens(),
                mistralAiUsage.getTotalTokens()
        );
    }

    public static FinishReason finishReasonFrom(String mistralAiFinishReason) {
        if (mistralAiFinishReason == null) {
            return null;
        }
        switch (mistralAiFinishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
                return TOOL_EXECUTION;
            case "content_filter":
                return CONTENT_FILTER;
            case "model_length":
            default:
                return null;
        }
    }

    public static AiMessage aiMessageFrom(MistralAiChatCompletionResponse response) {
        MistralAiChatMessage aiMistralMessage = response.getChoices().get(0).getMessage();
        List<MistralAiToolCall> toolCalls = aiMistralMessage.getToolCalls();
        if (!isNullOrEmpty(toolCalls)){
            return AiMessage.from(toToolExecutionRequests(toolCalls));
        }
        return  AiMessage.from(aiMistralMessage.getContent());
    }

    public static List<ToolExecutionRequest> toToolExecutionRequests(List<MistralAiToolCall> mistralAiToolCalls) {
        return mistralAiToolCalls.stream()
                .filter(toolCall -> toolCall.getType() == MistralAiToolType.FUNCTION)
                .map(DefaultMistralAiHelper::toToolExecutionRequest)
                .collect(toList());
    }

    public static ToolExecutionRequest toToolExecutionRequest(MistralAiToolCall mistralAiToolCall) {
        return ToolExecutionRequest.builder()
                .id(mistralAiToolCall.getId())
                .name(mistralAiToolCall.getFunction().getName())
                .arguments(mistralAiToolCall.getFunction().getArguments())
                .build();
    }

    static List<MistralAiTool> toMistralAiTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(DefaultMistralAiHelper::toMistralAiTool)
                .collect(toList());
    }

    static MistralAiTool toMistralAiTool(ToolSpecification toolSpecification) {
        MistralAiFunction function = MistralAiFunction.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toMistralAiParameters(toolSpecification.parameters()))
                .build();
        return MistralAiTool.from(function);
    }

    static MistralAiParameters toMistralAiParameters(ToolParameters parameters){
        if (parameters == null) {
            return MistralAiParameters.builder().build();
        }
        return MistralAiParameters.from(parameters);
    }

    static MistralAiResponseFormat toMistralAiResponseFormat(String responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        switch (responseFormat) {
            case "text":
                return MistralAiResponseFormat.fromType(MistralAiResponseFormatType.TEXT);
            case "json_object":
                return MistralAiResponseFormat.fromType(MistralAiResponseFormatType.JSON_OBJECT);
            default:
                throw new IllegalArgumentException("Unknown response format: " + responseFormat);
        }
    }

    static String getHeaders(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false).map(header -> {
            String headerKey = header.component1();
            String headerValue = header.component2();
            if (headerKey.equals("Authorization")) {
                headerValue = maskAuthorizationHeaderValue(headerValue);
            }
            return String.format("[%s: %s]", headerKey, headerValue);
        }).collect(Collectors.joining(", "));
    }

    private static String maskAuthorizationHeaderValue(String authorizationHeaderValue) {
        try {
            Matcher matcher = MISTRAI_API_KEY_BEARER_PATTERN.matcher(authorizationHeaderValue);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String bearer = matcher.group(1);
                String token = matcher.group(2);
                matcher.appendReplacement(sb, bearer + " " + token.substring(0, 2) + "..." + token.substring(token.length() - 2));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return "Error while masking Authorization header value";
        }
    }
}
