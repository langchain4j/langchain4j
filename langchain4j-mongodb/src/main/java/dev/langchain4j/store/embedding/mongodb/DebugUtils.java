package dev.langchain4j.store.embedding.mongodb;

import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class DebugUtils {
    private static final Logger log = LoggerFactory.getLogger(DebugUtils.class);

    private static JsonWriterSettings settings = JsonWriterSettings.builder().indent(true).build();

    public static String asJson(List<Bson> bsonArray) {
        return bsonArray.stream()
                .map(DebugUtils::asJson)
                .collect(Collectors.joining(", "));

    }

    public static String asJson(Bson bson) {
        return bson.toBsonDocument().toJson(settings);

    }

    public static void printDebugInfoAboutVectorSearchInMongoDB(int dimensions, List<Bson> pipeline){
        log.debug("problably the index is not yet created. Please create the index in MongoDB Atlas");
        log.debug("check your mappings at MongoDB Atlas. The minimum mapping should look similar as: {}", DebugUtils.getExampleMapping(dimensions));

        log.debug("to test the aggregation pipeline you can use this json: {}", DebugUtils.asJson(pipeline));
    }


    private static String getExampleMapping(int dimensions) {


        return "{\n" +
                "  \"mappings\": {\n" +
                "    \"dynamic\": false,\n" +
                "    \"fields\": {\n" +
                "      \n" +
                "      \"embedding\": {\n" +
                "        \"dimensions\": " +dimensions+ ",\n" +
                "        \"similarity\": \"cosine\",\n" +
                "        \"type\": \"knnVector\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                " \n" +
                "}";

    }
}
