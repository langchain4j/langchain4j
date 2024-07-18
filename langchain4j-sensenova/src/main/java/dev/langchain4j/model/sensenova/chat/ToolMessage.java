package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;


@ToString
@EqualsAndHashCode
@Builder
public final class ToolMessage implements Message {

    private final Role role = Role.TOOL;

    @Getter
    @SerializedName("tool_call_id")
    private final String toolCallId;

    @Getter
    private final String content;

    @SerializedName("tool_calls")
    private final List<ToolCall> toolCalls;

    @Override
    public Role getRole() {
        return role;
    }

}