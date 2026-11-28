package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicThinking {

    public String type;
    public Integer budgetTokens;
    public String display;

    public AnthropicThinking() {}

    public AnthropicThinking(String type, Integer budgetTokens, String display) {
        this.type = type;
        this.budgetTokens = budgetTokens;
        this.display = display;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "AnthropicThinking [", "]")
                .add("type=" + type)
                .add("budgetTokens=" + budgetTokens)
                .add("display=" + display)
                .toString();
    }
}
