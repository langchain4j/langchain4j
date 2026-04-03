package dev.langchain4j.model.openai.internal.embedding;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

class OpenAiEmbeddingDeserializer extends JsonDeserializer<List<Float>> {
    private static final TypeReference<List<Float>> FLOAT_LIST_TYPE_REFERENCE = new TypeReference<>() {};

    @Override
    public List<Float> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        JsonToken token = jsonParser.currentToken();
        if (token == JsonToken.START_ARRAY) {
            return jsonParser.readValueAs(FLOAT_LIST_TYPE_REFERENCE);
        } else if (token == JsonToken.VALUE_STRING) {
            String base64 = jsonParser.getValueAsString();
            byte[] decodedBytes = Base64.getDecoder().decode(base64);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            int floatCount = decodedBytes.length / Float.BYTES;
            List<Float> result = new ArrayList<>(floatCount);

            for (int i = 0; i < floatCount; i++) {
                result.add(byteBuffer.getFloat());
            }
            return result;
        } else {
            throw new IOException("Illegal embedding: " + token);
        }
    }
}
