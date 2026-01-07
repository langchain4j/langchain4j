package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicContent {

    public String type;

    // when type = "text"
    public String text;

    // when type = "tool_use"
    public String id;
    public String name;
    public Map<String, Object> input;

    // when type = "thinking"
    public String thinking;
    public String signature;

    // when type = "redacted_thinking"
    public String data;

    // when type ends with "_tool_result" (e.g., web_search_tool_result, code_execution_tool_result)
    public String toolUseId;
    public Object content; // Raw content - structure varies by tool type
}
