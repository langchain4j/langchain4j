package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
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
