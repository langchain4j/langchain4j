package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A chain for conversing with a specified {@link ChatLanguageModel} while maintaining a memory of the conversation.
 * Includes a default {@link ChatMemory} (a message window with maximum 10 messages), which can be overridden.
 * <br>
 * Chains are not going to be developed further, it is recommended to use {@link AiServices} instead.
 */
public class ConversationalChain implements Chain<String, String> {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemory chatMemory;

    private ConversationalChain(ChatLanguageModel chatLanguageModel, ChatMemory chatMemory) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.chatMemory = chatMemory == null ? MessageWindowChatMemory.withMaxMessages(10) : chatMemory;
    }

    public static ConversationalChainBuilder builder() {
        return new ConversationalChainBuilder();
    }

    @Override
    public String execute(String userMessage) {

        chatMemory.add(userMessage(ensureNotBlank(userMessage, "userMessage")));

        AiMessage aiMessage = chatLanguageModel.generate(chatMemory.messages()).content();

        chatMemory.add(aiMessage);

        return aiMessage.text();
    }

    public static class ConversationalChainBuilder {
        private ChatLanguageModel chatLanguageModel;
        private ChatMemory chatMemory;

        ConversationalChainBuilder() {
        }

        public ConversationalChainBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
            return this;
        }

        public ConversationalChainBuilder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public ConversationalChain build() {
            return new ConversationalChain(this.chatLanguageModel, this.chatMemory);
        }

        public String toString() {
            return "ConversationalChain.ConversationalChainBuilder(chatLanguageModel=" + this.chatLanguageModel + ", chatMemory=" + this.chatMemory + ")";
        }
    }
}
