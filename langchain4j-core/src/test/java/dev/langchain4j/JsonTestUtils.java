package dev.langchain4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Internal
public class JsonTestUtils {

    public static String jsonify(String s) {
        try {
            return new ObjectMapper().writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
