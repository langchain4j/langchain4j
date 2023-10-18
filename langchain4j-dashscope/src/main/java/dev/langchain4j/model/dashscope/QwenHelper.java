package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Optional;

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

    static Message toQwenMessage(ChatMessage message) {
        return Message.builder()
                .role(roleFrom(message))
                .content(message.text())
                .build();
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

    static TokenUsage tokenUsageFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getUsage)
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
}
