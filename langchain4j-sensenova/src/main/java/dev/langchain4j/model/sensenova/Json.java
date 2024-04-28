package dev.langchain4j.model.sensenova;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static dev.langchain4j.model.sensenova.AssistantMessageTypeAdapter.ASSISTANT_MESSAGE_TYPE_ADAPTER_FACTORY;

class Json {
	public static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
			.registerTypeAdapterFactory(ASSISTANT_MESSAGE_TYPE_ADAPTER_FACTORY)
			.setPrettyPrinting()
			.create();

	static String toJson(Object o) {
		return GSON.toJson(o);
	}

	static <T> T fromJson(String json, Class<T> type) {
		return GSON.fromJson(json, type);
	}
}