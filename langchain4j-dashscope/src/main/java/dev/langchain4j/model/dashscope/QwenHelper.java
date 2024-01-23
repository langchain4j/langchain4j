package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.dashscope.common.Role.*;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.stream.Collectors.toList;

class QwenHelper {

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
        return Message.builder()
                .role(roleFrom(message))
                .content(toSingleText(message))
                .build();
    }

    static String toSingleText(ChatMessage message) {
        switch (message.type()) {
            case USER:
                UserMessage userMessage = (UserMessage) message;
                return userMessage.hasSingleText() ?
                        ((TextContent) userMessage.contents().get(0)).text() : "";
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
        return MultiModalMessage.builder()
                .role(roleFrom(message))
                .content(toMultiModalContents(message))
                .build();
    }

    static List<Map<String, Object>> toMultiModalContents(ChatMessage message) {
        switch (message.type()) {
            case USER:
                UserMessage userMessage = (UserMessage) message;
                return userMessage.contents().stream()
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
                } else {
                    imageContent = image.base64Data();
                }
                return Collections.singletonMap("image", imageContent);
            case TEXT:
                return Collections.singletonMap("text", ((TextContent) content).text());
            default:
                return Collections.emptyMap();
        }
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
                        .orElse(""));
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
                .orElse("");
    }

    static TokenUsage tokenUsageFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(new TokenUsage(null, null));
    }

    static TokenUsage tokenUsageFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(new TokenUsage(null, null));
    }

    static FinishReason finishReasonFrom(GenerationResult result) {
        String finishReason = Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getFinishReason)
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
}
