package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Builder
@ToString
@EqualsAndHashCode
public class AnthropicToolSchema {

    @Builder.Default
    public String type = "object";
    public Map<String, Map<String, Object>> properties;
    public List<String> required;
}
