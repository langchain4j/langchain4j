package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper for Pinecone Struct conversion
 */
class PineconeHelper {

    private PineconeHelper() throws InstantiationException {
        throw new InstantiationException("can't instantiate this class");
    }

    public static Map<String, Object> structToMetadata(Map<String, Value.KindCase> metadataTypeMap, Map<String, Value> filedsMap) {
        Map<String, Object> metadataMap = new HashMap<>(metadataTypeMap.size());
        for (Map.Entry<String, Value.KindCase> metadataType : metadataTypeMap.entrySet()) {
            String metadataKey = metadataType.getKey();
            Value.KindCase type = metadataType.getValue();

            if (filedsMap.containsKey(metadataKey)) {
                switch (type) {
                    case NUMBER_VALUE:
                        metadataMap.put(metadataKey, filedsMap.get(metadataKey).getNumberValue());
                        break;
                    case STRING_VALUE:
                        metadataMap.put(metadataKey, filedsMap.get(metadataKey).getStringValue());
                        break;
                    default:
                        throw new UnsupportedOperationException("Pinecone does not support type " + type);
                }
            }
        }

        return metadataMap;
    }

    public static Struct metadataToStruct(TextSegment textSegment, Map<String, Value.KindCase> metadataTypeMap, String metadataTextKey) {
        Metadata metadata = textSegment.metadata();
        Struct.Builder metadataBuilder = Struct.newBuilder()
                .putFields(metadataTextKey, Value.newBuilder().setStringValue(textSegment.text()).build());
        if (metadataTypeMap != null && !metadataTypeMap.isEmpty() && !metadata.toMap().isEmpty()) {
            for (Map.Entry<String, Value.KindCase> metadataType : metadataTypeMap.entrySet()) {
                String metadataKey = metadataType.getKey();
                Value.KindCase type = metadataType.getValue();

                switch (type) {
                    case NUMBER_VALUE:
                        metadataBuilder.putFields(metadataKey, Value.newBuilder().setNumberValue(metadata.getDouble(metadataKey)).build());
                        break;
                    case STRING_VALUE:
                        metadataBuilder.putFields(metadataKey, Value.newBuilder().setStringValue(metadata.getString(metadataKey)).build());
                        break;
                    default:
                        throw new UnsupportedOperationException("Pinecone does not support type " + type);
                }
            }
        }
        return metadataBuilder.build();
    }
}
