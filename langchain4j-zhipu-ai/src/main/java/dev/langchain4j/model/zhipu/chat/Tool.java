package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Tool {

    private ToolType type;
    private Function function;
    private Retrieval retrieval;
    private WebSearch webSearch;

    public Tool(Function function) {
        this.type = ToolType.FUNCTION;
        this.function = function;
        this.retrieval = null;
        this.webSearch = null;
    }

    public static Tool from(Function function) {
        return new Tool(function);
    }
}
