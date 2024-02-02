package dev.langchain4j.model.qianfan.client;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Json {
    public static final Gson GSON;

    Json() {
    }

    static String toJson(Object o) {
        return GSON.toJson(o);
    }

    static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    static {
        GSON = (new GsonBuilder()).setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).registerTypeAdapterFactory(
                MessageTypeAdapter.MESSAGE_TYPE_ADAPTER_FACTORY).create();
    }
}