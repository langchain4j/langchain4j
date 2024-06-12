package dev.langchain4j.model.ark;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChunk;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunction;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatTool;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;

import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

class ArkHelper {

    static List<com.volcengine.ark.runtime.model.completion.chat.ChatMessage> toArkMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(ArkHelper::toArkMessage)
                .collect(toList());
    }

    static com.volcengine.ark.runtime.model.completion.chat.ChatMessage toArkMessage(ChatMessage message) {
        ChatMessageRole role = roleFrom(message);
        com.volcengine.ark.runtime.model.completion.chat.ChatMessage.Builder builder = com.volcengine.ark.runtime.model.completion.chat.ChatMessage.builder()
                .role(role)
                .content(toSingleText(message));

        if (message instanceof AiMessage) {
            List<ToolExecutionRequest> toolExecutionRequests = ((AiMessage) message).toolExecutionRequests();
            List<ChatToolCall> toolCalls = toolExecutionRequests.stream()
                    .map(toolexec -> new ChatToolCall(toolexec.id(), "function", new ChatFunctionCall(toolexec.name(), toolexec.arguments())))
                    .collect(toList());
            builder.toolCalls(toolCalls);
            builder.toolCallId(toolCalls.stream().map(ChatToolCall::getId).collect(Collectors.joining()));
        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
            builder.toolCallId(toolExecutionResultMessage.id());
            builder.content(toolExecutionResultMessage.text());
        }
        return builder.build();
    }

    static String toSingleText(ChatMessage message) {
        switch (message.type()) {
            case USER:
                return ((UserMessage) message).contents()
                        .stream()
                        .filter(TextContent.class::isInstance)
                        .map(TextContent.class::cast)
                        .map(TextContent::text)
                        .collect(Collectors.joining("\n"));
            case AI:
                return ((AiMessage) message).text();
            case SYSTEM:
                return ((SystemMessage) message).text();
            case TOOL_EXECUTION_RESULT:
                return ((ToolExecutionResultMessage) message).text();
            default:
                return "";
        }
    }

    static ChatMessageRole roleFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            return ChatMessageRole.ASSISTANT;
        } else if (message instanceof SystemMessage) {
            return ChatMessageRole.SYSTEM;
        } else if (message instanceof ToolExecutionResultMessage) {
            return ChatMessageRole.TOOL;
        } else {
            return ChatMessageRole.USER;
        }
    }

    static boolean hasAnswer(ChatCompletionResult result) {
        return Optional.of(result)
                .map(ChatCompletionResult::getChoices)
                .filter(choices -> !choices.isEmpty())
                .isPresent();
    }

    static String answerFrom(ChatCompletionResult result) {
        return Optional.of(result)
                .map(ChatCompletionResult::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(ChatCompletionChoice::getMessage)
                .map(com.volcengine.ark.runtime.model.completion.chat.ChatMessage::stringContent)
                // Compatible with some older models.
                .orElseGet(() -> Optional.of(result)
                        .map(ChatCompletionResult::getObject)
                        .orElseThrow(NullPointerException::new));
    }

    static boolean hasAnswer(ChatCompletionChunk result) {
        return Optional.of(result)
                .map(ChatCompletionChunk::getChoices)
                .filter(choices -> !choices.isEmpty())
                .isPresent();
    }

    static String answerFrom(ChatCompletionChunk result) {
        return Optional.of(result)
                .map(ChatCompletionChunk::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(ChatCompletionChoice::getMessage)
                .map(com.volcengine.ark.runtime.model.completion.chat.ChatMessage::stringContent)
                // Compatible with some older models.
                .orElse(null);
    }

    static TokenUsage tokenUsageFrom(ChatCompletionResult result) {
        return Optional.of(result)
                .map(ChatCompletionResult::getUsage)
                .map(usage -> new TokenUsage((int) usage.getPromptTokens(), (int) usage.getCompletionTokens()))
                .orElse(null);
    }

    static FinishReason finishReasonFrom(ChatCompletionResult result) {
        String finishReason = Optional.of(result)
                .map(ChatCompletionResult::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(ChatCompletionChoice::getFinishReason)
                .orElse("");

        switch (finishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
                return TOOL_EXECUTION;
            default:
                return null;
        }
    }

    static FinishReason finishReasonFrom(ChatCompletionChunk result) {
        String finishReason = Optional.of(result)
                .map(ChatCompletionChunk::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(ChatCompletionChoice::getFinishReason)
                .orElse("");

        switch (finishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
                return TOOL_EXECUTION;
            default:
                return null;
        }
    }

    /**
     * build ChatTool coll from ToolSpecification coll
     *
     * @param toolSpecifications {@link ToolSpecification}
     * @return {@link ChatTool}
     */
    static List<ChatTool> toToolFunctions(Collection<ToolSpecification> toolSpecifications) {
        if (isNullOrEmpty(toolSpecifications)) {
            return Collections.emptyList();
        }

        return toolSpecifications.stream()
                .map(tool -> {
                    ToolParameters parameters = tool.parameters();
                    return new ChatFunction.Builder()
                            .name(tool.name())
                            .description(tool.description())
                            .parameters(ArkToolParameter.builder()
                                    .properties(parameters != null ? parameters.properties() : emptyMap())
                                    .required(parameters != null ? parameters.required() : emptyList())
                                    .build());
                })
                .map(builder -> new ChatTool("function", builder.build()))
                .collect(Collectors.toList());
    }

    static AiMessage aiMessageFrom(ChatCompletionResult response) {
        com.volcengine.ark.runtime.model.completion.chat.ChatMessage assistantMessage = response.getChoices().get(0).getMessage();

        List<ChatToolCall> toolCalls = assistantMessage.getToolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            return new AiMessage(toolCallsFrom(response));
        }

        ChatFunctionCall functionCall = assistantMessage.getFunctionCall();
        if (functionCall != null) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .id(assistantMessage.getToolCallId())
                    .name(functionCall.getName())
                    .arguments(functionCall.getArguments())
                    .build();
            return new AiMessage(Collections.singletonList((toolExecutionRequest)));
        }

        return new AiMessage(answerFrom(response));
    }

    private static List<ToolExecutionRequest> toolCallsFrom(ChatCompletionResult result) {
        List<ChatToolCall> toolCalls = Optional.of(result)
                .map(ChatCompletionResult::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(ChatCompletionChoice::getMessage)
                .map(com.volcengine.ark.runtime.model.completion.chat.ChatMessage::getToolCalls)
                .orElseThrow(IllegalStateException::new);

        return toolCalls.stream()
                .map(toolCall -> ToolExecutionRequest.builder()
                        .id(toolCall.getId())
                        .name(toolCall.getFunction().getName())
                        .arguments(toolCall.getFunction().getArguments())
                        .build())
                .collect(Collectors.toList());
    }
}