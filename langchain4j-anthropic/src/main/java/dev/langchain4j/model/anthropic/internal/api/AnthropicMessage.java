package dev.langchain4j.model.anthropic.internal.api;

import lombok.*;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicMessage {

    public AnthropicRole role;
    public List<AnthropicMessageContent> content;
}
