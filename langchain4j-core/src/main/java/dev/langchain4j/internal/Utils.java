package dev.langchain4j.internal;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

public class Utils {

  public static <T> T getOrDefault(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }

  public static <T> T getOrDefault(T value, Supplier<T> defaultValueSupplier) {
    return value != null ? value : defaultValueSupplier.get();
  }

  public static boolean isNullOrBlank(String string) {
    return string == null || string.trim().isEmpty();
  }

  public static boolean isNotNullOrBlank(String string) {
    return !isNullOrBlank(string);
  }

  public static boolean areNotNullOrBlank(String... strings) {
    if (strings == null || strings.length == 0) {
      return false;
    }

    for (String string : strings) {
      if (isNullOrBlank(string)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isNullOrEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  @Deprecated
  public static boolean isCollectionEmpty(Collection<?> collection) {
    return isNullOrEmpty(collection);
  }

  public static String repeat(String string, int times) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < times; i++) {
      sb.append(string);
    }
    return sb.toString();
  }

  public static String randomUUID() {
    return UUID.randomUUID().toString();
  }

  public static String generateUUIDFrom(String input) {
    try {
      byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) sb.append(String.format("%02x", b));
      return UUID.nameUUIDFromBytes(sb.toString().getBytes(UTF_8)).toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String quoted(String string) {
    if (string == null) {
      return "null";
    }
    return "\"" + string + "\"";
  }

  public static String firstChars(String string, int numberOfChars) {
    if (string == null) {
      return null;
    }
    return string.length() > numberOfChars ? string.substring(0, numberOfChars) : string;
  }

  public static byte[] read(String url) {
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setRequestMethod("GET");

      int responseCode = connection.getResponseCode();

      if (responseCode == HTTP_OK) {
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }

        return outputStream.toByteArray();
      } else {
        throw new RuntimeException("Error while reading: " + responseCode);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
