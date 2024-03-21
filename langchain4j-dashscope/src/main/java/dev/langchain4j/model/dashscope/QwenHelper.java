package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.dashscope.extension.aigc.generation.GenerationMessage;
import dev.langchain4j.model.dashscope.extension.aigc.generation.GenerationOutput;
import dev.langchain4j.model.dashscope.extension.aigc.generation.GenerationResult;
import dev.langchain4j.model.dashscope.extension.aigc.multimodalconversation.MultiModalConversationMessage;
import dev.langchain4j.model.dashscope.extension.aigc.multimodalconversation.MultiModalConversationOutput;
import dev.langchain4j.model.dashscope.extension.aigc.multimodalconversation.MultiModalConversationResult;
import dev.langchain4j.model.dashscope.extension.common.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.dashscope.common.Role.*;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.stream.Collectors.toList;

class QwenHelper {
    static Map<String, Object> toQwenToolsParameters(List<ToolSpecification> toolSpecifications) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(
                "tools",
                toolSpecifications.stream().map(toolSpecification -> {
                    Map<String, Object> functionDef = new HashMap<>();
                    functionDef.put("type", "function");
                    functionDef.put("function", toolSpecification);
                    return functionDef;
                }).collect(Collectors.toList())
        );
        return parameters;
    }

    static List<Message> toQwenMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(QwenHelper::toQwenMessage)
                .collect(toList());
    }

    static List<Message> toQwenMessages(Iterable<ChatMessage> messages) {
        LinkedList<Message> qwenMessages = new LinkedList<>();
        messages.forEach(message -> qwenMessages.add(toQwenMessage(message)));
        return qwenMessages;
    }

    static Message toQwenMessage(ChatMessage message) {
        Message.MessageBuilder<?, ?> builder;
        switch (message.type()) {
            case AI:
                if (((AiMessage) message).hasToolExecutionRequests()) {
                    builder = ToolCallMessage.builder()
                            .toolCalls(toolCallsFrom(((AiMessage) message)));
                } else {
                    builder = Message.builder();
                }
                break;
            case TOOL_EXECUTION_RESULT:
                builder = ToolResultMessage.builder()
                        .name(((ToolExecutionResultMessage) message).toolName());
                break;
            default:
                builder = Message.builder();
        }
        return builder
                .role(roleFrom(message))
                .content(toSingleText(message))
                .build();
    }

    private static List<ToolCall> toolCallsFrom(AiMessage aiMessage) {
        return aiMessage
                .toolExecutionRequests()
                .stream()
                .map(toolExecutionRequest -> ToolCall
                        .builder()
                        .id(toolExecutionRequest.id())
                        .type("function")
                        .function(FunctionCall
                                .builder()
                                .name(toolExecutionRequest.name())
                                .arguments(toolExecutionRequest.arguments())
                                .build()
                        )
                        .build()
                )
                .collect(Collectors.toList());
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

    static List<MultiModalMessage> toQwenMultiModalMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(QwenHelper::toQwenMultiModalMessage)
                .collect(toList());
    }

    static MultiModalMessage toQwenMultiModalMessage(ChatMessage message) {
        MultiModalMessage.MultiModalMessageBuilder<?, ?> builder;
        switch (message.type()) {
            case AI:
                if (((AiMessage) message).hasToolExecutionRequests()) {
                    builder = ToolCallMultiModalMessage.builder()
                            .toolCalls(toolCallsFrom(((AiMessage) message)));
                } else {
                    builder = MultiModalMessage.builder();
                }
                break;
            case TOOL_EXECUTION_RESULT:
                builder = ToolResultMultiModalMessage.builder()
                        .name(((ToolExecutionResultMessage) message).toolName());
                break;
            default:
                builder = MultiModalMessage.builder();
        }
        return builder
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
        if (message instanceof AiMessage) {
            return ASSISTANT.getValue();
        } else if (message instanceof SystemMessage) {
            return SYSTEM.getValue();
        } else {
            return USER.getValue();
        }
    }

    static boolean hasAnswer(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .isPresent();
    }

    static Response<AiMessage> responseFrom(GenerationResult generationResult) {
        String answer = answerFrom(generationResult);
        List<ToolExecutionRequest> toolExecutionRequests = toolExecutionRequestsFrom(generationResult);
        return Response.from(
                toolExecutionRequests.isEmpty()
                        ? AiMessage.from(answer)
                        : AiMessage.from(toolExecutionRequests),
                tokenUsageFrom(generationResult),
                finishReasonFrom(generationResult)
        );
    }

    static String answerFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(GenerationOutput.Choice::getMessage)
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

    static Response<AiMessage> responseFrom(MultiModalConversationResult generationResult) {
        String answer = answerFrom(generationResult);
        List<ToolExecutionRequest> toolExecutionRequests = toolExecutionRequestsFrom(generationResult);
        return Response.from(
                toolExecutionRequests.isEmpty()
                        ? AiMessage.from(answer)
                        : AiMessage.from(toolExecutionRequests),
                tokenUsageFrom(generationResult),
                finishReasonFrom(generationResult)
        );
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
        String finishReason = Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(GenerationOutput.Choice::getFinishReason)
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

    private static List<ToolExecutionRequest> toolExecutionRequestsFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(GenerationOutput.Choice::getMessage)
                .map(GenerationMessage::getToolCalls)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(toolCall -> ToolExecutionRequest
                        .builder()
                        .id(toolCall.getId())
                        .name(toolCall.getFunction().getName())
                        .arguments(toolCall.getFunction().getArguments())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<ToolExecutionRequest> toolExecutionRequestsFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalConversationMessage::getToolCalls)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(toolCall -> ToolExecutionRequest
                        .builder()
                        .id(toolCall.getId())
                        .name(toolCall.getFunction().getName())
                        .arguments(toolCall.getFunction().getArguments())
                        .build())
                .collect(Collectors.toList());
    }

    public static boolean isMultimodalModel(String modelName) {
        // for now, multimodal models start with "qwen-vl"
        return modelName.startsWith("qwen-vl");
    }
}
