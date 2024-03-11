package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class Tool {

    private final ToolType type;
    private final Function function;
    private final Retrieval retrieval;
    @SerializedName("web_search")
    private final WebSearch webSearch;

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
