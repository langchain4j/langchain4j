package dev.langchain4j.model.mistralai;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
enum MistralAiToolChoiceName {

    @SerializedName("auto") AUTO,
    @SerializedName("any") ANY,
    @SerializedName("none") NONE;

    MistralAiToolChoiceName() {
    }
}
