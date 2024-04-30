package dev.langchain4j.model.anthropic.internal.api;

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

    public String model;
    public List<AnthropicMessage> messages;
    public String system;
    public int maxTokens;
    public List<String> stopSequences;
    public boolean stream;
    public Double temperature;
    public Double topP;
    public Integer topK;
    public List<AnthropicTool> tools;
}
