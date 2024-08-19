package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.segment.TextSegment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper for Pinecone Struct conversion
 */
class PineconeHelper {

    private PineconeHelper() throws InstantiationException {
        throw new InstantiationException("can't instantiate this class");
    }

    public static Map<String, Object> structToMetadata(Map<String, Value> filedsMap, String metadataTextKey) {
        if ((filedsMap.size() == 1 && filedsMap.containsKey(metadataTextKey)) || filedsMap.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> metadataMap = new HashMap<>(filedsMap.size() - 1);
        for (Map.Entry<String, Value> entry : filedsMap.entrySet()) {
            String key = entry.getKey();
            Value value = entry.getValue();

            if (value.hasNumberValue()) {
                metadataMap.put(key, value.getNumberValue());
            } else if (value.hasStringValue()) {
                metadataMap.put(key, value.getStringValue());
            }
        }

        return metadataMap;
    }

    public static Struct metadataToStruct(TextSegment textSegment, String metadataTextKey) {
        Map<String, Object> metadata = textSegment.metadata().toMap();
        Struct.Builder metadataBuilder = Struct.newBuilder()
                .putFields(metadataTextKey, Value.newBuilder().setStringValue(textSegment.text()).build());
        if (!metadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String || value instanceof UUID) {
                    metadataBuilder.putFields(key, Value.newBuilder().setStringValue(value.toString()).build());
                } else if (value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double) {
                    metadataBuilder.putFields(key, Value.newBuilder().setNumberValue(((Number) value).doubleValue()).build());
                }
            }
        }

        return metadataBuilder.build();
    }
}
