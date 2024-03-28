package dev.langchain4j.model.dashscope.extension.common;

import com.alibaba.dashscope.common.Message;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class ToolResultMessage extends Message {
    /**
     * The tool name.
     */
    String name;
}
