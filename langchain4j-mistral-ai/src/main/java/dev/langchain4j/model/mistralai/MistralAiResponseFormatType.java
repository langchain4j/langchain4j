package dev.langchain4j.model.mistralai;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

/**
 * Represents the value of the 'type' field in the response_format parameter of the MistralAi Chat completions request.
 * <p>
 * Current values are:
 * <ul>
 *     <li>{@link MistralAiResponseFormatType#TEXT}</li>
 *     <li>{@link MistralAiResponseFormatType#JSON_OBJECT}</li>
 * </ul>
 */
@Getter
public enum MistralAiResponseFormatType {

    @SerializedName("text") TEXT,
    @SerializedName("json_object") JSON_OBJECT;

    MistralAiResponseFormatType() {
    }

    /**
     * Returns the string representation in lowercase of the response format type.
     */
    public String toString() {
        return this.name().toLowerCase();
    }

}
