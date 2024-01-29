package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Delta {
    private final String content;
    @SerializedName("tool_calls")
    private final List<ToolCall> toolCalls;

    private Delta(Builder builder) {
        this.content = builder.content;
        this.toolCalls = builder.toolCalls;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Delta && this.equalTo((Delta) another);
        }
    }

    private boolean equalTo(Delta another) {
        return Objects.equals(this.content, another.content)
                && Objects.equals(this.toolCalls, another.toolCalls);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.content);
        h += (h << 5) + Objects.hashCode(this.toolCalls);
        return h;
    }

    @Override
    public String toString() {
        return "Delta{"
                + "content=" + this.content
                + ", toolCalls=" + this.toolCalls
                + "}";
    }

    public static final class Builder {
        private String content;
        private List<ToolCall> toolCalls;

        private Builder() {
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            if (toolCalls != null) {
                this.toolCalls = Collections.unmodifiableList(toolCalls);
            }

            return this;
        }

        public Delta build() {
            return new Delta(this);
        }
    }
}
