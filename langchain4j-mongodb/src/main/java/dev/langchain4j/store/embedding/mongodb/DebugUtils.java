package dev.langchain4j.store.embedding.mongodb;

import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;

import java.util.List;
import java.util.stream.Collectors;

public class DebugUtils {

    private static JsonWriterSettings settings = JsonWriterSettings.builder().indent(true).build();

    public static String asJson(List<Bson> bsonArray) {
        return bsonArray.stream()
                .map(DebugUtils::asJson)
                .collect(Collectors.joining(", "));

    }
    public static String asJson(Bson bson) {
        return bson.toBsonDocument().toJson(settings);

    }
}
