package dev.langchain4j.model.zhipu.chat;

import java.util.Objects;

public final class ToolCall {
    private final String id;
    private final Integer index;
    private final ToolType type;
    private final FunctionCall function;

    private ToolCall(Builder builder) {
        this.id = builder.id;
        this.index = builder.index;
        this.type = builder.type;
        this.function = builder.function;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public Integer getIndex() {
        return index;
    }

    public ToolType getType() {
        return type;
    }

    public FunctionCall getFunction() {
        return function;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof ToolCall && this.equalTo((ToolCall) another);
        }
    }

    private boolean equalTo(ToolCall another) {
        return Objects.equals(this.id, another.id)
                && Objects.equals(this.index, another.index)
                && Objects.equals(this.type, another.type)
                && Objects.equals(this.function, another.function);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.id);
        h += (h << 5) + Objects.hashCode(this.index);
        h += (h << 5) + Objects.hashCode(this.type);
        h += (h << 5) + Objects.hashCode(this.function);
        return h;
    }

    @Override
    public String toString() {
        return "ToolCall{"
                + "id=" + this.id
                + ", index=" + this.index
                + ", type=" + this.type
                + ", function=" + this.function
                + "}";
    }

    public static final class Builder {
        private String id;
        private Integer index;
        private ToolType type;
        private FunctionCall function;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder type(ToolType type) {
            this.type = type;
            return this;
        }

        public Builder function(FunctionCall function) {
            this.function = function;
            return this;
        }

        public ToolCall build() {
            return new ToolCall(this);
        }
    }
}
