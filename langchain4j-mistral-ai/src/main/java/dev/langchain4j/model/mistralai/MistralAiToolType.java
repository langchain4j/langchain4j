package dev.langchain4j.model.mistralai;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public enum MistralAiToolType {

    @SerializedName("function") FUNCTION;

    MistralAiToolType() {
    }
}
