package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// TODO check all annotations, everywhere
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AnthropicChatMessage {

    private AnthropicRole role;
    private String content;
}
