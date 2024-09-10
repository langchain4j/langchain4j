package dev.langchain4j.store.embedding.tablestore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.internal.ValidationUtils;

class TablestoreUtils {

    private static final int MAX_DEBUG_LOG_LENGTH = 100;
    private static final Gson GSON = new GsonBuilder().create();

    protected static float[] parseEmbeddingString(String embeddingString) {
        ValidationUtils.ensureNotBlank(embeddingString, "embeddingString");
        return GSON.fromJson(embeddingString, float[].class);
    }

    protected static String embeddingToString(float[] embedding) {
        ValidationUtils.ensureNotNull(embedding, "embedding");
        return GSON.toJson(embedding);
    }

    protected static String maxLogOrNull(String str) {
        if (str == null) {
            return null;
        }
        int max = MAX_DEBUG_LOG_LENGTH;
        if (str.length() <= max) {
            return str;
        }
        return str.substring(0, max) + "......";
    }

}
