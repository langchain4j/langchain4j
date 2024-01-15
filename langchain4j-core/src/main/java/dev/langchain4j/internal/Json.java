package dev.langchain4j.internal;

import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.json.JsonCodecFactory;

import java.io.IOException;
import java.io.InputStream;

public class Json {

    private static final JsonCodec CODEC = ServiceHelper.loadFactoryService(
            JsonCodecFactory.class, JsonCodecFactory::create, GsonJsonCodec::new);

    public static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }

    public static InputStream toInputStream(Object o, Class<?> type) throws IOException {
        return CODEC.toInputStream(o, type);
    }

    public interface JsonCodec {

        String toJson(Object o);

        <T> T fromJson(String json, Class<T> type);

        InputStream toInputStream(Object o, Class<?> type) throws IOException;
    }
}
