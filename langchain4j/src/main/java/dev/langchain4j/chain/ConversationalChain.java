package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;

/**
 * A chain for interacting with a specified ChatLanguageModel while maintaining a memory of the conversation.
 * Includes a default ChatMemory (a message window with a capacity of 10), which can be overridden.
 */
public class ConversationalChain implements Chain<String, String> {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemory chatMemory;

    @Builder
    private ConversationalChain(ChatLanguageModel chatLanguageModel, ChatMemory chatMemory) {
        this.chatLanguageModel = chatLanguageModel;
        this.chatMemory = chatMemory;
    }

    @Override
    public String execute(String userMessage) {
        chatMemory.add(userMessage(userMessage));
        List<ChatMessage> messages = chatMemory.messages();

        AiMessage aiMessage = chatLanguageModel.sendMessages(messages).get();

        chatMemory.add(aiMessage);
        return aiMessage.text();
    }
}
