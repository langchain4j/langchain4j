package dev.langchain4j.model.bedrock;

import java.util.List;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAnthropicMessage {

    private String role;
    private List<BedrockAnthropicContent> content;

    public BedrockAnthropicMessage(final String role, final List<BedrockAnthropicContent> content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(final String role) {
        this.role = role;
    }

    public List<BedrockAnthropicContent> getContent() {
        return content;
    }

    public void setContent(final List<BedrockAnthropicContent> content) {
        this.content = content;
    }
}
