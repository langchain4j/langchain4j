package dev.langchain4j.internal;

import dev.langchain4j.spi.json.JsonCodecFactory;
import dev.langchain4j.spi.ServiceHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public class Json {

  private static final JsonCodec CODEC = loadCodec();

  private static JsonCodec loadCodec() {
    Collection<JsonCodecFactory> factories = ServiceHelper.loadFactories(JsonCodecFactory.class);
    for (JsonCodecFactory factory : factories) {
      return factory.create();
    }
    // fallback to default
    return new GsonJsonCodec();
  }



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
