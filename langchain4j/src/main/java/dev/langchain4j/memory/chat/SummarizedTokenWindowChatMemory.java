package dev.langchain4j.memory.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.prompt.SummarizedSystemPrompt;
import dev.langchain4j.memory.chat.prompt.SummarizerPrompt;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class SummarizedTokenWindowChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(SummarizedTokenWindowChatMemory.class);

    private final Object id;
    private final Integer maxTokens;
    private final Tokenizer tokenizer;
    private final ChatMemoryStore store;
    private final ChatLanguageModel chatLanguageModel;

    protected SummarizedTokenWindowChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.maxTokens = ensureGreaterThanZero(builder.maxTokens, "maxTokens");
        this.tokenizer = ensureNotNull(builder.tokenizer, "tokenizer");
        this.store = ensureNotNull(builder.store, "store");
        this.chatLanguageModel = ensureNotNull(builder.chatLanguageModel, "chatLanguageModel");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = messages();
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> maybeSystemMessage = findSystemMessage(messages);
            if (maybeSystemMessage.isPresent()) {
                // Not supporting overwriting system messages for now
                log.trace("SummarizedTokenWindowChatMemory does not support overwriting system messages for now");
                return;
            } else {
                // Create a special summarized system message
                message = generateSummarizedSystemMessage((SystemMessage) message);
            }
        }
        messages.add(message);
        ensureCapacity(messages, maxTokens, tokenizer);
        store.updateMessages(id, messages);
    }

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .findAny();
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new LinkedList<>(store.getMessages(id));
        ensureCapacity(messages, maxTokens, tokenizer);
        return messages;
    }

    private void ensureCapacity(List<ChatMessage> messages, int maxTokens, Tokenizer tokenizer) {

        List<ChatMessage> evictedMessages = new ArrayList<>();

        int currentTokenCount = tokenizer.estimateTokenCountInMessages(messages);
        while (currentTokenCount > maxTokens) {

            int messageToEvictIndex = 0;
            if (messages.get(0) instanceof SystemMessage) {
                messageToEvictIndex = 1;
            }

            if (messageToEvictIndex == messages.size()) {
                log.warn("No messages to evict to comply with the capacity requirement");
                break;
            }

            ChatMessage evictedMessage = messages.remove(messageToEvictIndex);
            evictedMessages.add(evictedMessage);

            int tokenCountOfEvictedMessage = tokenizer.estimateTokenCountInMessage(evictedMessage);
            log.trace("Evicting the following message ({} tokens) to comply with the capacity requirement: {}",
                    tokenCountOfEvictedMessage, evictedMessage);
            currentTokenCount -= tokenCountOfEvictedMessage;

            if (evictedMessage instanceof AiMessage && ((AiMessage) evictedMessage).hasToolExecutionRequests()) {
                while (messages.size() > messageToEvictIndex
                        && messages.get(messageToEvictIndex) instanceof ToolExecutionResultMessage) {
                    evictedMessages.add(messages.get(messageToEvictIndex));
                    ChatMessage orphanToolExecutionResultMessage = messages.remove(messageToEvictIndex);
                    currentTokenCount -= tokenizer.estimateTokenCountInMessage(orphanToolExecutionResultMessage);
                    log.trace("Evicting ({} token) orphan {}", currentTokenCount, orphanToolExecutionResultMessage);
                }
            }
        }

        if (!evictedMessages.isEmpty()) {
            addEvictedMessagesToSummary(messages, evictedMessages);
        }
    }

    public static Optional<String> extractOriginalSystemMessage(SystemMessage systemMessage) {
        return extractJsonContent(systemMessage, "originalInstructions");
    }

    public static Optional<String> extractSummary(SystemMessage systemMessage) {
        return extractJsonContent(systemMessage, "conversationSummary");
    }

    private static Optional<String> extractJsonContent(SystemMessage systemMessage, String fieldName) {
        if (systemMessage == null || systemMessage.text() == null) {
            return Optional.empty();
        }

        try {
            JsonObject jsonObject = JsonParser.parseString(systemMessage.text()).getAsJsonObject();
            if (jsonObject.has(fieldName) && jsonObject.get(fieldName).isJsonPrimitive()) {
                return Optional.of(jsonObject.get(fieldName).getAsString().trim());
            }
        } catch (JsonSyntaxException e) {
            log.error("Error parsing JSON content", e);
        }
        return Optional.empty();
    }

    /**
     * Generates a new summary given an existing summary and a new message. Only supports UserMessage and AiMessage without
     * tool execution requests/responses for now.
     * @param messages the list of messages in the chat memory.
     * @param evictedMessages the list of ChatMessages to be evicted.
     */
    private void addEvictedMessagesToSummary(List<ChatMessage> messages, List<ChatMessage> evictedMessages) {
        StringBuilder messagesToSummarize = new StringBuilder();

        for (ChatMessage evictedMessage : evictedMessages) {
            switch (evictedMessage.type()) {
                case USER:
                    messagesToSummarize.append("User: ").append(((UserMessage) evictedMessage).singleText()).append("\n");
                    break;
                case AI:
                    if (((AiMessage) evictedMessage).hasToolExecutionRequests()) {
                        log.warn("Tool execution requests are not supported in this method");
                    } else {
                        messagesToSummarize.append("AI: ").append(((AiMessage) evictedMessage).text()).append("\n");
                    }
                    break;
            }
        }

        Optional<SystemMessage> maybeSystemMessage = findSystemMessage(messages);
        if (maybeSystemMessage.isPresent()) {
            // Not supporting overwriting system messages for now
            String existingSummary = extractSummary(maybeSystemMessage.get()).orElse("");
            String newSummary = generateMessageSummary(messagesToSummarize.toString(), existingSummary);
            SystemMessage updatedSummarizedSystemMessage = generateSummarizedSystemMessage(maybeSystemMessage.get().text(), newSummary);

            // Replace the existing system message with the updated one
            int systemMessageIndex = messages.indexOf(maybeSystemMessage.get());
            if (systemMessageIndex != -1) {
                messages.set(systemMessageIndex, updatedSummarizedSystemMessage);
            } else {
                // If the system message is not found in the list (which shouldn't happen),
                // add the updated system message to the beginning of the list
                messages.add(0, updatedSummarizedSystemMessage);
            }
        }
    }

    /**
     * Converts the original system message into a SummarizedSystemPrompt with placeholder values for the original
     * message and the summary.
     * @param originalSystemMessage
     * @return
     */
    private SystemMessage generateSummarizedSystemMessage(SystemMessage originalSystemMessage) {
        SummarizedSystemPrompt summarizerPrompt = new SummarizedSystemPrompt(originalSystemMessage.text(), "none, the conversation has just begun.");
        Prompt prompt = StructuredPromptProcessor.toPrompt(summarizerPrompt);
        return prompt.toSystemMessage();
    }

    private SystemMessage generateSummarizedSystemMessage(String originalSystemMessageText, String summary) {
        SummarizedSystemPrompt summarizedMessage = new SummarizedSystemPrompt(originalSystemMessageText, summary);
        Prompt prompt = StructuredPromptProcessor.toPrompt(summarizedMessage);
        return prompt.toSystemMessage();
    }

    private String generateMessageSummary(String messagesToSummarize, String existingSummary) {
        SummarizerPrompt summarizerPrompt = new SummarizerPrompt(messagesToSummarize, existingSummary);
        Prompt prompt = StructuredPromptProcessor.toPrompt(summarizerPrompt);
        String newSummary = chatLanguageModel.generate(prompt.text());
        return newSummary;
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
        private Integer maxTokens;
        private Tokenizer tokenizer;
        private ChatMemoryStore store = new InMemoryChatMemoryStore();
        private ChatLanguageModel chatLanguageModel;

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        public Builder maxTokens(Integer maxTokens, Tokenizer tokenizer) {
            this.maxTokens = maxTokens;
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder chatMemoryStore(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        public Builder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
            return this;
        }

        public SummarizedTokenWindowChatMemory build() {
            return new SummarizedTokenWindowChatMemory(this);
        }
    }

    public static SummarizedTokenWindowChatMemory withMaxTokens(int maxTokens, Tokenizer tokenizer, ChatLanguageModel chatLanguageModel) {
        return builder().maxTokens(maxTokens, tokenizer).chatLanguageModel(chatLanguageModel).build();
    }
}