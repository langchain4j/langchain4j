package dev.langchain4j.model.dashscope.extension.common;

import com.alibaba.dashscope.common.MultiModalMessage;
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
public class ToolResultMultiModalMessage extends MultiModalMessage {
    /**
     * The tool name.
     */
    String name;
}
