package dev.langchain4j.model.dashscope.extension.aigc.generation;

import com.alibaba.dashscope.common.Message;
import com.google.gson.annotations.SerializedName;
import dev.langchain4j.model.dashscope.extension.common.ToolCall;
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
public class GenerationMessage extends Message {
    @SerializedName("tool_calls")
    List<ToolCall> toolCalls;
    /**
     * The tool name.
     */
    String name;
}
