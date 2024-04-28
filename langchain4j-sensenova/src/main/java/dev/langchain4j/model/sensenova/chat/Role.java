package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;

public enum Role {
	@SerializedName("user") USER,
	@SerializedName("assistant") ASSISTANT,
	@SerializedName("tool") TOOL,
}