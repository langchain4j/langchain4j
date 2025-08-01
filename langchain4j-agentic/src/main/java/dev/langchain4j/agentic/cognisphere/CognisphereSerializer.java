package dev.langchain4j.agentic.cognisphere;

import java.util.ServiceLoader;

/**
 * Utility class for serializing Cognisphere objects to JSON format.
 */
public class CognisphereSerializer {

    static final CognisphereJsonCodec CODEC = loadCodec();

    private static CognisphereJsonCodec loadCodec() {
        for (CognisphereJsonCodec codec : ServiceLoader.load(CognisphereJsonCodec.class)) {
            return codec;
        }
        return new JacksonCognisphereJsonCodec();
    }

    /**
     * Serializes a Cognisphere into a JSON string.
     *
     * @param cognisphere Cognisphere to be serialized.
     * @return A JSON string with the contents of the Cognisphere.
     * @see CognisphereSerializer For details on deserialization.
     */
    public static String toJson(DefaultCognisphere cognisphere) {
        return CODEC.toJson(cognisphere);
    }

    /**
     * Deserializes a JSON string into a Cognisphere object.
     *
     * @param json JSON string to be deserialized.
     * @return A Cognisphere object constructed from the JSON.
     * @see CognisphereSerializer For details on serialization.
     */
    public static DefaultCognisphere fromJson(String json) {
        return CODEC.fromJson(json).normalizeAfterDeserialization();
    }
}
