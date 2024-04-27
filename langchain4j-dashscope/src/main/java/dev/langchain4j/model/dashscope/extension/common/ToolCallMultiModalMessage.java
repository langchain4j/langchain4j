package dev.langchain4j.model.dashscope.extension.common;

import com.alibaba.dashscope.common.MultiModalMessage;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class ToolCallMultiModalMessage extends MultiModalMessage {
    @SerializedName("tool_calls")
    List<ToolCall> toolCalls;
}
