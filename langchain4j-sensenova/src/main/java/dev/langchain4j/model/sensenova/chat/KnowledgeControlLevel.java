package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;

public enum KnowledgeControlLevel {
	@SerializedName("normal") NORMAL,
	@SerializedName("high") HIGH,
}
