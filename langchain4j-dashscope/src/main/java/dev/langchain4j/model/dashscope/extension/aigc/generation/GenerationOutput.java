package dev.langchain4j.model.dashscope.extension.aigc.generation;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public final class GenerationOutput {
    // output text
    private String text;

    @SerializedName("finish_reason")
    private String finishReason;

    private List<Choice> choices;

    @Data
    static public class Choice {
        @SerializedName("finish_reason")
        private String finishReason;

        private GenerationMessage message;
    }
}
