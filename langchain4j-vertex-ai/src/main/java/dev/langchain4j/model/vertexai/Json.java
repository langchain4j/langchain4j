package dev.langchain4j.model.vertexai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

class Json {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    static String toJson(Object o) {
        return GSON.toJson(o);
    }
}
