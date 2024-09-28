package dev.langchain4j.model.voyageai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * This class aims to handle Voyage "base64" compress on embedding.
 *
 * <p>Using custom deserializer to deserialize "base64" to normal embedding List</p>
 */
class VoyageAiEmbeddingDeserializer extends StdDeserializer<List<EmbeddingResponse.EmbeddingData>> {

    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    public VoyageAiEmbeddingDeserializer() {
        this(null);
    }

    protected VoyageAiEmbeddingDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public List<EmbeddingResponse.EmbeddingData> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode dataNode = p.getCodec().readTree(p);

        if (dataNode != null && dataNode.isArray()) {
            List<EmbeddingResponse.EmbeddingData> embeddings = new ArrayList<>();

            for (JsonNode data : dataNode) {
                JsonNode embeddingNode = data.get("embedding");

                List<Float> embedding;
                if (embeddingNode != null && embeddingNode.isArray()) {
                    embedding = new ArrayList<>();
                    for (JsonNode embeddingValue : embeddingNode) {
                        embedding.add(embeddingValue.floatValue());
                    }
                } else if (embeddingNode != null && embeddingNode.isTextual()) {
                    embedding = decodeBase64ToFloatList(embeddingNode.asText());
                } else {
                    throw new RuntimeException("Unexpected embedding " + embeddingNode);
                }

                embeddings.add(new EmbeddingResponse.EmbeddingData(data.get("object").asText(), embedding, data.get("index").asInt()));
            }

            return embeddings;
        }

        throw new RuntimeException("Expect data is ArrayNode, but is " + dataNode);
    }

    private List<Float> decodeBase64ToFloatList(String base64String) {
        byte[] bytes = BASE64_DECODER.decode(base64String);
        List<Float> embedding = new ArrayList<>();
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        while (byteBuffer.remaining() >= Float.BYTES) {
            embedding.add(byteBuffer.getFloat());
        }

        return embedding;
    }
}
