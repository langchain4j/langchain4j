package dev.langchain4j.model.workersai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a request for AI chat completion.
 * Contains a list of messages that form part of the chat conversation.
 */
@Data
public class WorkersAiChatCompletionRequest {

    private List<Message> messages;

    /**
     * Represents a message in the AI chat.
     * Each message has a role and content.
     */
    @Data @AllArgsConstructor
    public static class Message {
        private MessageRole role;
        private String content;
        /**
         * Default constructor.
         */
        @SuppressWarnings("unused")
        public Message() {}
    }

    /**
     * Defines the roles a message can have in the chat conversation.
     */
    @SuppressWarnings("unused")
    public enum MessageRole {
        /**
         * Directive for the prompt
         */
        system,
        /**
         * The message is from the AI.
         */
        ai,
        /**
         * The message is from the user.
         */
        user
    }

    /**
     * Constructs an empty WorkerAiChatCompletionRequest with an empty list of messages.
     */
    public WorkersAiChatCompletionRequest() {
        this.messages = new ArrayList<>();
    }

    /**
     * Constructs a WorkerAiChatCompletionRequest with an initial message.
     *
     * @param role The role of the initial message.
     * @param content The content of the initial message.
     */
    public WorkersAiChatCompletionRequest(MessageRole role, String content) {
        this();
        addMessage(role, content);
    }

    /**
     * Adds a new message to the chat completion request.
     *
     * @param role The role of the message.
     * @param content The content of the message.
     */
    public void addMessage(MessageRole role, String content) {
        Message message = new Message(role, content);
        this.messages.add(message);
    }

}

