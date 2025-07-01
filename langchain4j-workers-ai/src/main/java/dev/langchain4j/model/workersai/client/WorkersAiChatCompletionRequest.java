package dev.langchain4j.model.workersai.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a request for AI chat completion.
 * Contains a list of messages that form part of the chat conversation.
 */
public class WorkersAiChatCompletionRequest {

    private List<Message> messages;

    public List<Message> getMessages() {
        return this.messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof WorkersAiChatCompletionRequest)) return false;
        final WorkersAiChatCompletionRequest other = (WorkersAiChatCompletionRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$messages = this.getMessages();
        final Object other$messages = other.getMessages();
        if (this$messages == null ? other$messages != null : !this$messages.equals(other$messages)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof WorkersAiChatCompletionRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $messages = this.getMessages();
        result = result * PRIME + ($messages == null ? 43 : $messages.hashCode());
        return result;
    }

    public String toString() {
        return "WorkersAiChatCompletionRequest(messages=" + this.getMessages() + ")";
    }

    /**
     * Represents a message in the AI chat.
     * Each message has a role and content.
     */
    public static class Message {
        private MessageRole role;
        private String content;

        /**
         * Default constructor.
         */
        @SuppressWarnings("unused")
        public Message() {
        }

        public Message(MessageRole role, String content) {
            this.role = role;
            this.content = content;
        }

        public MessageRole getRole() {
            return this.role;
        }

        public String getContent() {
            return this.content;
        }

        public void setRole(MessageRole role) {
            this.role = role;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof Message)) return false;
            final Message other = (Message) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$role = this.getRole();
            final Object other$role = other.getRole();
            if (this$role == null ? other$role != null : !this$role.equals(other$role)) return false;
            final Object this$content = this.getContent();
            final Object other$content = other.getContent();
            if (this$content == null ? other$content != null : !this$content.equals(other$content)) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof Message;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $role = this.getRole();
            result = result * PRIME + ($role == null ? 43 : $role.hashCode());
            final Object $content = this.getContent();
            result = result * PRIME + ($content == null ? 43 : $content.hashCode());
            return result;
        }

        public String toString() {
            return "WorkersAiChatCompletionRequest.Message(role=" + this.getRole() + ", content=" + this.getContent() + ")";
        }
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
     * @param role    The role of the initial message.
     * @param content The content of the initial message.
     */
    public WorkersAiChatCompletionRequest(MessageRole role, String content) {
        this();
        addMessage(role, content);
    }

    /**
     * Adds a new message to the chat completion request.
     *
     * @param role    The role of the message.
     * @param content The content of the message.
     */
    public void addMessage(MessageRole role, String content) {
        Message message = new Message(role, content);
        this.messages.add(message);
    }

}

