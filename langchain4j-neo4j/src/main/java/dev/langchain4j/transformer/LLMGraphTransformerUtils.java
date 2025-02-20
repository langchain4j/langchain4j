package dev.langchain4j.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LLMGraphTransformerUtils {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static Map<String, Object> toMap(Object object) {
        return OBJECT_MAPPER.convertValue(object, new TypeReference<>() {});
    }

    public static String getStringFromListOfMaps(List<Map<String, String>> list) {
        try {
            return OBJECT_MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseJson(String jsonString) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.readValue(jsonString, new TypeReference<>() {});
    }

    public static String generateMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    public static String removeBackticks(String input) {
        return input; // .replace("`", "");
        // TODO        return input.replace("`", "");
    }

    public static final List<Map<String, String>> EXAMPLES_PROMPT = Arrays.asList(
            Map.of(
                    "text",
                            "Adam is a software engineer in Microsoft since 2009, and last year he got an award as the Best Talent",
                    "head", "Adam",
                    "head_type", "Person",
                    "relation", "WORKS_FOR",
                    "tail", "Microsoft",
                    "tail_type", "Company"),
            Map.of(
                    "text",
                            "Adam is a software engineer in Microsoft since 2009, and last year he got an award as the Best Talent",
                    "head", "Adam",
                    "head_type", "Person",
                    "relation", "HAS_AWARD",
                    "tail", "Best Talent",
                    "tail_type", "Award"),
            Map.of(
                    "text", "Microsoft is a tech company that provide several products such as Microsoft Word",
                    "head", "Microsoft Word",
                    "head_type", "Product",
                    "relation", "PRODUCED_BY",
                    "tail", "Microsoft",
                    "tail_type", "Company"),
            Map.of(
                    "text", "Microsoft Word is a lightweight app that accessible offline",
                    "head", "Microsoft Word",
                    "head_type", "Product",
                    "relation", "HAS_CHARACTERISTIC",
                    "tail", "lightweight app",
                    "tail_type", "Characteristic"),
            Map.of(
                    "text", "Microsoft Word is a lightweight app that accessible offline",
                    "head", "Microsoft Word",
                    "head_type", "Product",
                    "relation", "HAS_CHARACTERISTIC",
                    "tail", "accessible offline",
                    "tail_type", "Characteristic"));
}
