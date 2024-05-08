package dev.langchain4j.memory.chat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * This chat memory operates a concatenation of all users prompts and it is designed to be used
 * for a stateful data extraction.
 * <p>
 * Once added, a {@link SystemMessage} is always retained.
 * Only one {@code SystemMessage} can be held at a time.
 * If a new {@code SystemMessage} with the same content is added, it is ignored.
 * If a new {@code SystemMessage} with different content is added, the previous {@code SystemMessage} is removed.
 * <p>
 * There is only one {@link UserMessage} at time that at each interaction is recreated from the PromptTemplate using as
 * variables the concatenation of the values of those variables during the entire lifespan of the memory.
 * <p>
 * All {@link AiMessage}s are discarded.
 * <p>
 * The state of chat memory is stored in {@link ChatMemoryStore} ({@link InMemoryChatMemoryStore} is used by default).
 */
public class UserMessagesConcatChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(UserMessagesConcatChatMemory.class);

    private final Object id;
    private final ChatMemoryStore store;

    private UserMessagesConcatChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.store = ensureNotNull(builder.store, "store");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = messages();

        if (message instanceof SystemMessage) {
            Optional<SystemMessage> systemMessage = findSystemMessage(messages);
            if (systemMessage.isPresent()) {
                if (systemMessage.get().equals(message)) {
                    return; // do not add the same system message
                } else {
                    messages.remove(systemMessage.get()); // need to replace existing system message
                }
            }
            messages.add(message);

        } else if (message instanceof UserMessage) {
            Optional<UserMessage> userMessage = findUserMessage(messages);
            if (userMessage.isPresent()) {
                messages.remove(userMessage.get()); // need to replace existing user message
                messages.add(concatUserMessages(userMessage.get(), (UserMessage) message));
            } else {
                messages.add(message);
            }
        }

        store.updateMessages(id, messages);
    }

    private static UserMessage concatUserMessages(UserMessage oldMessage, UserMessage newMessage) {
        Map<String, Object> oldVars = oldMessage.variables();
        Map<String, Object> newVars = newMessage.variables();

        Map<String, Object> concatVars = new HashMap<>(oldVars);
        newVars.forEach((key, value) -> concatVars.compute(key, (k, v) -> v == null ? value : v + ". " + value));

        Prompt prompt = PromptTemplate.from(newMessage.template()).apply(concatVars);
        UserMessage concatMessage = newMessage.name() != null ? UserMessage.from(newMessage.name(), prompt.text()) : UserMessage.from(prompt.text());
        return concatMessage.fromTemplate(newMessage.template()).withVariables(concatVars);
    }

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findAny();
    }

    private static Optional<UserMessage> findUserMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof UserMessage)
                .map(message -> (UserMessage) message)
                .findAny();
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new LinkedList<>(store.getMessages(id));
        return messages;
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object id = "default";
        private ChatMemoryStore store = new InMemoryChatMemoryStore();

        /**
         * @param id The ID of the {@link ChatMemory}.
         *           If not provided, a "default" will be used.
         * @return builder
         */
        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        /**
         * @param store The chat memory store responsible for storing the chat memory state.
         *              If not provided, an {@link InMemoryChatMemoryStore} will be used.
         * @return builder
         */
        public Builder chatMemoryStore(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        public UserMessagesConcatChatMemory build() {
            return new UserMessagesConcatChatMemory(this);
        }
    }

    public static UserMessagesConcatChatMemory build() {
        return builder().build();
    }
}
