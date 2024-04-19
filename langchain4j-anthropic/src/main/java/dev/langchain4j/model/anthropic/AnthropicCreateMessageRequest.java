package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AnthropicCreateMessageRequest {

    String model;
    List<AnthropicMessage> messages;
    String system;
    int maxTokens;
    List<String> stopSequences;
    boolean stream;
    Double temperature;
    Double topP;
    Integer topK;
    List<AnthropicTool> tools;
}
