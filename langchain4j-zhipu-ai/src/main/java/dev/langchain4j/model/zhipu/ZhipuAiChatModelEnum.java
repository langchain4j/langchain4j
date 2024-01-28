package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public enum ZhipuAiChatModelEnum {
    @SerializedName("glm-4") GLM_4,
    @SerializedName("glm-3-turbo") GLM_3_TURBO,
    @SerializedName("chatglm_turbo") CHATGLM_TURBO,
    ;
}
