package dev.langchain4j.model.dashscope.extension.aigc.multimodalconversation;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class MultiModalConversationOutput {
    // output message.
    private List<Choice> choices;

    @Data
    static public class Choice {
        @SerializedName("finish_reason")
        private String finishReason;

        private MultiModalConversationMessage message;
    }
}
