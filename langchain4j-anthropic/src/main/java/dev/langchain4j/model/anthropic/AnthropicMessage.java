package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class AnthropicMessage {

    AnthropicRole role;
    List<AnthropicMessageContent> content;
}
