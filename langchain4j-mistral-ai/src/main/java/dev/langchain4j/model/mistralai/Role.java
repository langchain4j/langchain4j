package dev.langchain4j.model.mistralai;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public enum Role {

    @SerializedName("system") SYSTEM,
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT;

    private Role() {}

}
