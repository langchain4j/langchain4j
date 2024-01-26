package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.Builder;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A chain for conversing with a specified {@link ChatLanguageModel} while maintaining a memory of the conversation.
 * Includes a default {@link ChatMemory} (a message window with maximum 10 messages), which can be overridden.
 * <br>
 * It is recommended to use {@link AiServices} instead, as it is more powerful.
 */
public class ConversationalChain implements Chain<String, String> {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemory chatMemory;

    @Builder
    private ConversationalChain(ChatLanguageModel chatLanguageModel, ChatMemory chatMemory) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.chatMemory = chatMemory == null ? MessageWindowChatMemory.withMaxMessages(10) : chatMemory;
    }

    @Override
    public String execute(String userMessage) {

        chatMemory.add(userMessage(ensureNotBlank(userMessage, "userMessage")));

        AiMessage aiMessage = chatLanguageModel.generate(chatMemory.messages()).content();

        chatMemory.add(aiMessage);

        return aiMessage.text();
    }
}
