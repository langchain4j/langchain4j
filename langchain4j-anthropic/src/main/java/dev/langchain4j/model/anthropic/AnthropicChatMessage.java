package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnthropicChatMessage {
    private AnthropicRole role;
    private String content;
}
