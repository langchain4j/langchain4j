package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

import java.util.List;
import java.util.stream.Collectors;

public class QwenParamHelper {
    private static final String SYSTEM_PREFIX = "<|system|>:";
    private static final String ASSISTANT_PREFIX = "<|assistant|>:";
    private static final String USER_PREFIX = "<|user|>:";

    /* Qwen prompt format:
     * <|system|>: ...
     *
     * <|user|>: ...
     *
     * <|assistant|>: ...
     *
     * <|user|>: ...
     * ...
     */
    public static String toQwenPrompt(List<ChatMessage> messages) {
        return messages.stream()
                .map(QwenParamHelper::toQwenMessage)
                .collect(Collectors.joining("\n\n"));
    }

    public static String toQwenMessage(ChatMessage message) {
        return prefixFrom(message) + message.text();
    }

    public static String prefixFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            return ASSISTANT_PREFIX;
        } else if (message instanceof SystemMessage) {
            return SYSTEM_PREFIX;
        } else {
            return USER_PREFIX;
        }
    }
}
