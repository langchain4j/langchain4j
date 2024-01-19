package dev.langchain4j.model.mistralai;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public enum MistralRoleName {

    @SerializedName("system") SYSTEM,
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT;

    private MistralRoleName() {}

}
