package dev.langchain4j.internal;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Json {

  private static final Gson GSON = new GsonBuilder()
    .setPrettyPrinting()
    .registerTypeAdapter(
      LocalDate.class,
      (JsonSerializer<LocalDate>) (localDate, type, context) -> new JsonPrimitive(localDate.format(ISO_LOCAL_DATE))
    )
    .registerTypeAdapter(
      LocalDate.class,
      (JsonDeserializer<LocalDate>) (json, type, context) -> LocalDate.parse(json.getAsString(), ISO_LOCAL_DATE)
    )
    .registerTypeAdapter(
      LocalDateTime.class,
      (JsonSerializer<LocalDateTime>) (localDateTime, type, context) ->
        new JsonPrimitive(localDateTime.format(ISO_LOCAL_DATE_TIME))
    )
    .registerTypeAdapter(
      LocalDateTime.class,
      (JsonDeserializer<LocalDateTime>) (json, type, context) ->
        LocalDateTime.parse(json.getAsString(), ISO_LOCAL_DATE_TIME)
    )
    .create();

  public static String toJson(Object o) {
    return GSON.toJson(o);
  }

  public static <T> T fromJson(String json, Class<T> type) {
    return GSON.fromJson(json, type);
  }

  public static InputStream toInputStream(Object o, Class type) throws IOException {
    try (
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8);
      JsonWriter jsonWriter = new JsonWriter(outputStreamWriter)
    ) {
      GSON.toJson(o, type, jsonWriter);
      jsonWriter.flush();

      return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }
  }
}
