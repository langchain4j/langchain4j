package dev.langchain4j.model.anthropic.internal.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@Getter
public class AnthropicMessage {

    public AnthropicRole role;
    public List<AnthropicMessageContent> content;
}
