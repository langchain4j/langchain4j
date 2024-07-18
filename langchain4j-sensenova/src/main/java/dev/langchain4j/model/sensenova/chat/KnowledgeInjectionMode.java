package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;

public enum KnowledgeInjectionMode {

	@SerializedName("concatenate") CONCATENATE,
	@SerializedName("override") OVERRIDE,
}
