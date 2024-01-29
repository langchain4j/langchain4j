package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

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

    public ToolType getType() {
        return type;
    }

    public Function getFunction() {
        return function;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public WebSearch getWebSearch() {
        return webSearch;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Tool && this.equalTo((Tool) another);
        }
    }

    private boolean equalTo(Tool another) {
        return Objects.equals(this.type, another.type)
                && Objects.equals(this.function, another.function)
                && Objects.equals(this.retrieval, another.retrieval)
                && Objects.equals(this.webSearch, another.webSearch)
                ;
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.type);
        h += (h << 5) + Objects.hashCode(this.function);
        return h;
    }

    @Override
    public String toString() {
        return "Tool{"
                + "type=" + this.type
                + ", function=" + this.function
                + "}";
    }
}
