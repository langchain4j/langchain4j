package dev.langchain4j.model.voyageai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.model.voyageai.VoyageAiJsonUtils.getObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class VoyageAiEmbeddingDeserializerTest {

    static final ObjectMapper OBJECT_MAPPER = getObjectMapper();

    @Mock
    JsonParser jsonParser;
    @Mock
    DeserializationContext deserializationContext;
    @Mock
    ObjectCodec objectCodec;

    @Test
    void should_deserialize_correctly() throws IOException {

        // given
        JsonNode dataNode = OBJECT_MAPPER.readTree("[{\"object\":\"embedding\",\"embedding\":[1.1, 2.2, 3.3],\"index\":0},{\"object\":\"embedding\",\"embedding\":[4.4, 5.5, 6.6],\"index\":1}]");
        doReturn(objectCodec).when(jsonParser).getCodec();
        doReturn(dataNode).when(objectCodec).readTree(jsonParser);
        VoyageAiEmbeddingDeserializer deserializer = new VoyageAiEmbeddingDeserializer();

        // when
        List<EmbeddingResponse.EmbeddingData> embeddingDataList = deserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(embeddingDataList).hasSize(2);
        assertThat(embeddingDataList.get(0).getEmbedding()).containsExactly(1.1f, 2.2f, 3.3f);
        assertThat(embeddingDataList.get(1).getEmbedding()).containsExactly(4.4f, 5.5f, 6.6f);
    }

    @Test
    void should_deserialize_base64_correctly() throws IOException {

        // given
        String base64_1 = encodeFloatArrayToBase64(new float[]{1.1f, 2.2f, 3.3f});
        String base64_2 = encodeFloatArrayToBase64(new float[]{4.4f, 5.5f, 6.6f});

        JsonNode dataNode = OBJECT_MAPPER.readTree(String.format("[{\"object\":\"embedding\",\"embedding\": \"%s\",\"index\":0},{\"object\":\"embedding\",\"embedding\":\"%s\",\"index\":1}]", base64_1, base64_2));
        doReturn(objectCodec).when(jsonParser).getCodec();
        doReturn(dataNode).when(objectCodec).readTree(jsonParser);
        VoyageAiEmbeddingDeserializer deserializer = new VoyageAiEmbeddingDeserializer();

        // when
        List<EmbeddingResponse.EmbeddingData> embeddingDataList = deserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(embeddingDataList).hasSize(2);
        assertThat(embeddingDataList.get(0).getEmbedding()).containsExactly(1.1f, 2.2f, 3.3f);
        assertThat(embeddingDataList.get(1).getEmbedding()).containsExactly(4.4f, 5.5f, 6.6f);
    }

    String encodeFloatArrayToBase64(float[] floatArray) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * Float.BYTES);
        for (float value : floatArray) {
            byteBuffer.putFloat(value);
        }
        byte[] bytes = byteBuffer.array();
        return Base64.getEncoder().encodeToString(bytes);
    }
}
