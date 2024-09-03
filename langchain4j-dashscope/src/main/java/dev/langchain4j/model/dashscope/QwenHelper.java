package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.*;
import com.alibaba.dashscope.utils.JsonUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ChatMessageType.*;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

@Slf4j
class QwenHelper {

    static List<Message> toQwenMessages(List<ChatMessage> messages) {
        return sanitizeMessages(messages)
                .stream()
                .map(QwenHelper::toQwenMessage)
                .collect(toList());
    }

    static List<Message> toQwenMessages(Iterable<ChatMessage> messages) {
        LinkedList<Message> qwenMessages = new LinkedList<>();
        messages.forEach(message -> qwenMessages.add(toQwenMessage(message)));
        return qwenMessages;
    }

    static Message toQwenMessage(ChatMessage message) {
        return Message.builder()
                .role(roleFrom(message))
                .content(toSingleText(message))
                .name(nameFrom(message))
                .toolCallId(toolCallIdFrom(message))
                .toolCalls(toolCallsFrom(message))
                .build();
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
                return ((AiMessage) message).hasToolExecutionRequests() ? "" : ((AiMessage) message).text();
            case SYSTEM:
                return ((SystemMessage) message).text();
            case TOOL_EXECUTION_RESULT:
                return ((ToolExecutionResultMessage) message).text();
            default:
                return "";
        }
    }

    static String nameFrom(ChatMessage message) {
        switch (message.type()) {
            case USER:
                return ((UserMessage) message).name();
            case TOOL_EXECUTION_RESULT:
                return ((ToolExecutionResultMessage) message).toolName();
            default:
                return null;
        }
    }

    static String toolCallIdFrom(ChatMessage message) {
        if (message.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
            return ((ToolExecutionResultMessage) message).id();
        }
        return null;
    }

    static List<ToolCallBase> toolCallsFrom(ChatMessage message) {
        if (message.type() == AI && ((AiMessage) message).hasToolExecutionRequests()) {
            return toToolCalls(((AiMessage) message).toolExecutionRequests());
        }
        return null;
    }

    static List<MultiModalMessage> toQwenMultiModalMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(QwenHelper::toQwenMultiModalMessage)
                .collect(toList());
    }

    static MultiModalMessage toQwenMultiModalMessage(ChatMessage message) {
        return MultiModalMessage.builder()
                .role(roleFrom(message))
                .content(toMultiModalContents(message))
                .build();
    }

    static List<Map<String, Object>> toMultiModalContents(ChatMessage message) {
        switch (message.type()) {
            case USER:
                return ((UserMessage) message).contents()
                        .stream()
                        .map(QwenHelper::toMultiModalContent)
                        .collect(Collectors.toList());
            case AI:
                return Collections.singletonList(
                        Collections.singletonMap("text", ((AiMessage) message).text()));
            case SYSTEM:
                return Collections.singletonList(
                        Collections.singletonMap("text", ((SystemMessage) message).text()));
            case TOOL_EXECUTION_RESULT:
                return Collections.singletonList(
                        Collections.singletonMap("text", ((ToolExecutionResultMessage) message).text()));
            default:
                return Collections.emptyList();
        }
    }

    static Map<String, Object> toMultiModalContent(Content content) {
        switch (content.type()) {
            case IMAGE:
                Image image = ((ImageContent) content).image();
                String imageContent;
                if (image.url() != null) {
                    imageContent = image.url().toString();
                    return Collections.singletonMap("image", imageContent);
                } else if (Utils.isNotNullOrBlank(image.base64Data())) {
                    // The dashscope sdk supports local file url: file://...
                    // Using the temporary directory for storing temporary files is a safe practice,
                    // as most operating systems will periodically clean up the contents of this directory
                    // or do so upon system reboot.
                    imageContent = saveImageAsTemporaryFile(image.base64Data(), image.mimeType());

                    // In this case, the dashscope sdk requires a mutable map.
                    HashMap<String, Object> contentMap = new HashMap<>(1);
                    contentMap.put("image", imageContent);
                    return contentMap;
                } else {
                    return Collections.emptyMap();
                }
            case TEXT:
                return Collections.singletonMap("text", ((TextContent) content).text());
            default:
                return Collections.emptyMap();
        }
    }

    private static String saveImageAsTemporaryFile(String base64Data, String mimeType) {
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        String tmpImageName = UUID.randomUUID().toString();
        if (Utils.isNotNullOrBlank(mimeType)) {
            // e.g. "image/png", "image/jpeg"...
            int lastSlashIndex = mimeType.lastIndexOf("/");
            if (lastSlashIndex >= 0 && lastSlashIndex < mimeType.length() - 1) {
                String imageSuffix = mimeType.substring(lastSlashIndex + 1);
                tmpImageName = tmpImageName + "." + imageSuffix;
            }
        }

        Path tmpImagePath = Paths.get(tmpDir, tmpImageName);
        byte[] data = Base64.getDecoder().decode(base64Data);
        try {
            Files.copy(new ByteArrayInputStream(data), tmpImagePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tmpImagePath.toAbsolutePath().toUri().toString();
    }

    static String roleFrom(ChatMessage message) {
        if (message.type() == AI) {
            return Role.ASSISTANT.getValue();
        } else if (message.type() == SYSTEM) {
            return Role.SYSTEM.getValue();
        } else if (message.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
            return Role.TOOL.getValue();
        } else {
            return Role.USER.getValue();
        }
    }

    static boolean hasAnswer(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .isPresent();
    }

    static String answerFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getContent)
                // Compatible with some older models.
                .orElseGet(() -> Optional.of(result)
                        .map(GenerationResult::getOutput)
                        .map(GenerationOutput::getText)
                        .orElseThrow(NullPointerException::new));
    }

    static boolean hasAnswer(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalMessage::getContent)
                .filter(contents -> !contents.isEmpty())
                .isPresent();
    }

    static String answerFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalMessage::getContent)
                .filter(contents -> !contents.isEmpty())
                .map(contents -> contents.get(0))
                .map(content -> content.get("text"))
                .map(String.class::cast)
                .orElseThrow(NullPointerException::new);
    }

    static TokenUsage tokenUsageFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(null);
    }

    static TokenUsage tokenUsageFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(null);
    }

    static FinishReason finishReasonFrom(GenerationResult result) {
        Choice choice = result.getOutput().getChoices().get(0);
        // Upon observation, when tool_calls occur, the returned finish_reason may be null or "stop", not "tool_calls".
        String finishReason = isNullOrEmpty(choice.getMessage().getToolCalls()) ?
                choice.getFinishReason() :
                "tool_calls";

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

    static FinishReason finishReasonFrom(MultiModalConversationResult result) {
        String finishReason = Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getFinishReason)
                .orElse("");

        switch (finishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            default:
                return null;
        }
    }

    public static boolean isMultimodalModel(String modelName) {
        // for now, multimodal models start with "qwen-vl"
        return modelName.startsWith("qwen-vl");
    }

    static List<ToolBase> toToolFunctions(Collection<ToolSpecification> toolSpecifications) {
        if (isNullOrEmpty(toolSpecifications)) {
            return Collections.emptyList();
        }

        return toolSpecifications.stream()
                .map(QwenHelper::toToolFunction)
                .collect(Collectors.toList());
    }

    static ToolBase toToolFunction(ToolSpecification toolSpecification) {
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toParameters(toolSpecification.parameters()))
                .build();
        return ToolFunction.builder().function(functionDefinition).build();
    }

    private static JsonObject toParameters(ToolParameters toolParameters) {
        return toolParameters == null ?
                JsonUtils.toJsonObject(Collections.emptyMap()) :
                JsonUtils.toJsonObject(toolParameters);
    }

    static AiMessage aiMessageFrom(GenerationResult result) {
        return isFunctionToolCalls(result) ?
                new AiMessage(functionToolCallsFrom(result)) : new AiMessage(answerFrom(result));
    }

    private static List<ToolExecutionRequest> functionToolCallsFrom(GenerationResult result) {
        List<ToolCallBase> toolCalls = Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getToolCalls)
                .orElseThrow(IllegalStateException::new);

        return toolCalls.stream()
                .filter(ToolCallFunction.class::isInstance)
                .map(ToolCallFunction.class::cast)
                .map(toolCall -> ToolExecutionRequest.builder()
                        .id(getOrDefault(toolCall.getId(), () -> toolCallIdFromMessage(result)))
                        .name(toolCall.getFunction().getName())
                        .arguments(toolCall.getFunction().getArguments())
                        .build())
                .collect(Collectors.toList());
    }

    static String toolCallIdFromMessage(GenerationResult result) {
        // Not sure about the difference between Message::getToolCallId() and ToolCallFunction::getId().
        // Encapsulate a method to get the ID using Message::getToolCallId() when ToolCallFunction::getId() is null.
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getToolCallId)
                .orElse(null);
    }

    static boolean isFunctionToolCalls(GenerationResult result) {
        Optional<List<ToolCallBase>> toolCallBases = Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getToolCalls);
        return toolCallBases.isPresent() && !isNullOrEmpty(toolCallBases.get());
    }

    private static List<ToolCallBase> toToolCalls(Collection<ToolExecutionRequest> toolExecutionRequests) {
        return toolExecutionRequests.stream()
                .map(QwenHelper::toToolCall)
                .collect(toList());
    }

    private static ToolCallBase toToolCall(ToolExecutionRequest toolExecutionRequest) {
        ToolCallFunction toolCallFunction = new ToolCallFunction();
        toolCallFunction.setId(toolExecutionRequest.id());
        ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
        callFunction.setName(toolExecutionRequest.name());
        callFunction.setArguments(toolExecutionRequest.arguments());
        toolCallFunction.setFunction(callFunction);
        return toolCallFunction;
    }

    static List<ChatMessage> sanitizeMessages(List<ChatMessage> messages) {
        LinkedList<ChatMessage> sanitizedMessages = messages.stream()
                .reduce(new LinkedList<>(), messageAccumulator(), messageCombiner());

        // Ensure the last message is a user/tool_execution_result message
        while(!sanitizedMessages.isEmpty() && !isInputMessageType(sanitizedMessages.getLast().type())) {
            ChatMessage removedMessage = sanitizedMessages.removeLast();
            log.warn("The last message should be a user/tool_execution_result message, but found: {}", removedMessage);
        }

        return sanitizedMessages;
    }

    private static BiFunction<LinkedList<ChatMessage>, ChatMessage, LinkedList<ChatMessage>> messageAccumulator() {
        return (acc, message) -> {
            ChatMessageType type = message.type();
            if (acc.isEmpty()) {
                // Ensure the first message is a system message or a user message.
                if (type == SYSTEM || type == USER) {
                    acc.add(message);
                } else {
                    log.warn("The first message should be a system message or a user message, but found: {}", message);
                }
                return acc;
            }

            if (type == SYSTEM) {
                // Ensure the system message is the first message.
                log.warn("The system message should be the first message. Drop existed messages: {}", acc);
                acc.clear();
                acc.add(message);
                return acc;
            }

            ChatMessageType lastType = acc.getLast().type();
            if (lastType == SYSTEM && type != USER) {
                // The first non-system message must be a user message.
                log.warn("The first non-system message must be a user message, but found: {}", message);
                return acc;
            }

            if (isInputMessageType(type) == isInputMessageType(lastType)) {
                // The list must be user/tool_execution_result and ai alternating messages.
                // Use the newest one when duplicated.
                ChatMessage removedMessage = acc.removeLast();
                log.warn("User/Tool-execution-result messages and AI messages should alternate. Drop duplicated message: {}", removedMessage);
            }

            acc.add(message);
            return acc;
        };
    }

    private static BinaryOperator<LinkedList<ChatMessage>> messageCombiner() {
        return (acc1, acc2) -> {
            throw new UnsupportedOperationException("Parallel stream not supported");
        };
    }

    private static boolean isInputMessageType(ChatMessageType messageType) {
        return messageType == USER || messageType == TOOL_EXECUTION_RESULT;
    }

    static ChatModelRequest createModelListenerRequest(GenerationParam request,
                                                       List<ChatMessage> messages,
                                                       List<ToolSpecification> toolSpecifications) {
        Double temperature = request.getTemperature() != null ? request.getTemperature().doubleValue() : null;
        return ChatModelRequest.builder()
                .model(request.getModel())
                .temperature(temperature)
                .topP(request.getTopP())
                .maxTokens(request.getMaxTokens())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelRequest createModelListenerRequest(MultiModalConversationParam request,
                                                       List<ChatMessage> messages,
                                                       List<ToolSpecification> toolSpecifications) {
        Double temperature = request.getTemperature() != null ? request.getTemperature().doubleValue() : null;
        return ChatModelRequest.builder()
                .model(request.getModel())
                .temperature(temperature)
                .topP(request.getTopP())
                .maxTokens(request.getMaxLength())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelResponse createModelListenerResponse(String responseId,
                                                         String responseModel,
                                                         Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }
}