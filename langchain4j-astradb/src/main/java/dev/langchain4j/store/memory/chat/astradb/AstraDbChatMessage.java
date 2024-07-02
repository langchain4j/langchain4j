package dev.langchain4j.store.memory.chat.astradb;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A chat message stored in AstraDB.
 */
@Setter @Getter @NoArgsConstructor
public class AstraDbChatMessage {

    /** Public Static to help build filters if any. */
    public static final String PROP_CHAT_ID = "chat_id";

    /** Public Static to help build filters if any. */
    public static final String PROP_MESSAGE = "text";

    /** Public Static to help build filters if any. */
    public static final String PROP_MESSAGE_TIME = "message_time";

    /** Public Static to help build filters if any. */
    public static final String PROP_MESSAGE_TYPE = "message_type";

    /** Public Static to help build filters if any. */
    public static final String PROP_TOOLS = "tools_execution_requests";

    /** Public Static to help build filters if any. */
    public static final String PROP_CONTENTS = "contents";

    /** Public Static to help build filters if any. */
    public static final String PROP_NAME = "name";

    @JsonProperty(PROP_CHAT_ID)
    private String chatId;

    @JsonProperty(PROP_MESSAGE_TYPE)
    private ChatMessageType messageType;

    @JsonProperty(PROP_MESSAGE_TIME)
    private Instant messageTime;

    @JsonProperty(PROP_NAME)
    private String name;

    @JsonProperty(PROP_MESSAGE)
    private String text;

    @JsonProperty(PROP_TOOLS)
    private List<ToolExecutionRequest> toolExecutionRequests;

    @JsonProperty(PROP_CONTENTS)
    private List<AstraDbContent> contents;

    @Data @NoArgsConstructor
    public static class ToolExecutionRequest {
        private String id;
        private String name;
        private String arguments;
        public ToolExecutionRequest(dev.langchain4j.agent.tool.ToolExecutionRequest lc4jTER) {
            this.id = lc4jTER.id();
            this.name = lc4jTER.name();
            this.arguments = lc4jTER.arguments();
        }
        public dev.langchain4j.agent.tool.ToolExecutionRequest asLc4j() {
            return dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                    .id(this.id)
                    .name(this.name)
                    .arguments(this.arguments)
                    .build();
        }
    }

    public AstraDbChatMessage(ChatMessage chatMessage) {
        this.chatId = chatMessage.type().name();
        this.messageType = chatMessage.type();
        this.messageTime = Instant.now();
        System.out.println("chatMessage.type(): " + chatMessage.type());
        switch (chatMessage.type()) {
            case SYSTEM:
                SystemMessage systemMessage = (SystemMessage) chatMessage;
                this.text = systemMessage.text();
            break;
            case USER:
                UserMessage userMessage = (UserMessage) chatMessage;
                this.name     = userMessage.name();
                if (userMessage.contents() != null) {
                    this.contents = userMessage.contents()
                            .stream()
                            .map(AstraDbContent::new).collect(Collectors.toList());
                }
                break;
            case AI:
                AiMessage aiMessage = (AiMessage) chatMessage;
                this.text = aiMessage.text();
                if (aiMessage.toolExecutionRequests() != null) {
                    this.toolExecutionRequests = aiMessage.toolExecutionRequests()
                            .stream().map(ToolExecutionRequest::new)
                            .collect(Collectors.toList());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown message type: " + chatMessage.type());
        }
    }

    /**
     * Downcast to {@link ChatMessage}.
     *
     * @return
     *      chatMessage interface
     */
    public ChatMessage toChatMessage() {
        switch (messageType) {
            case SYSTEM:
                return new SystemMessage(text);
            case USER:
                List<Content> targetContents = new ArrayList<>();
                if (this.contents!=null) {
                    targetContents.addAll(contents.stream()
                            .map(AstraDbContent::asContent)
                            .collect(Collectors.toList()));
                }
                if (name != null) {
                    return new UserMessage(name, targetContents);
                }
                return new UserMessage(targetContents);
            case AI:
                if (this.toolExecutionRequests != null) {
                    List< dev.langchain4j.agent.tool.ToolExecutionRequest> request = this.toolExecutionRequests
                            .stream().map(ToolExecutionRequest::asLc4j)
                            .collect(Collectors.toList());
                    if (text == null) {
                        return new AiMessage(request);
                    }
                    return new AiMessage(text, request);
                }
                return new AiMessage(text);
            default:
                throw new IllegalArgumentException("Unknown message type: " + messageType);
        }
    }
}
