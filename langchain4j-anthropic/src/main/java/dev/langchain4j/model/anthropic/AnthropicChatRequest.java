package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnthropicChatRequest {

    private String model;
    private int maxTokens;
    private Double temperature;
    private Double topP;
    private List<AnthropicChatMessage> messages;
    private boolean stream;

}
