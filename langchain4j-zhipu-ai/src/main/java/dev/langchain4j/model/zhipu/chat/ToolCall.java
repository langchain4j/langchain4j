package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ToolCall {
    private String id;
    private Integer index;
    private ToolType type;
    private FunctionCall function;

    public ToolCall() {
    }

    public ToolCall(String id, Integer index, ToolType type, FunctionCall function) {
        this.id = id;
        this.index = index;
        this.type = type;
        this.function = function;
    }

    public static ToolCallBuilder builder() {
        return new ToolCallBuilder();
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getIndex() {
        return this.index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public ToolType getType() {
        return this.type;
    }

    public void setType(ToolType type) {
        this.type = type;
    }

    public FunctionCall getFunction() {
        return this.function;
    }

    public void setFunction(FunctionCall function) {
        this.function = function;
    }

    public static class ToolCallBuilder {
        private String id;
        private Integer index;
        private ToolType type;
        private FunctionCall function;

        ToolCallBuilder() {
        }

        public ToolCallBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ToolCallBuilder index(Integer index) {
            this.index = index;
            return this;
        }

        public ToolCallBuilder type(ToolType type) {
            this.type = type;
            return this;
        }

        public ToolCallBuilder function(FunctionCall function) {
            this.function = function;
            return this;
        }

        public ToolCall build() {
            return new ToolCall(this.id, this.index, this.type, this.function);
        }
    }
}
