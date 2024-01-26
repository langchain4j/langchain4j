package dev.langchain4j.internal;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableList;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Utility methods.
 */
public class Utils {
  private Utils() {}

  /**
   * Returns the given value if it is not {@code null}, otherwise returns the given default value.
   * @param value The value to return if it is not {@code null}.
   * @param defaultValue The value to return if the value is {@code null}.
   * @return the given value if it is not {@code null}, otherwise returns the given default value.
   * @param <T> The type of the value.
   */
  public static <T> T getOrDefault(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }

  /**
   * Returns the given value if it is not {@code null}, otherwise returns the value returned by the given supplier.
   * @param value The value to return if it is not {@code null}.
   * @param defaultValueSupplier The supplier to call if the value is {@code null}.
   * @return the given value if it is not {@code null}, otherwise returns the value returned by the given supplier.
   * @param <T> The type of the value.
   */
  public static <T> T getOrDefault(T value, Supplier<T> defaultValueSupplier) {
    return value != null ? value : defaultValueSupplier.get();
  }

  /**
   * Is the given string {@code null} or blank?
   * @param string The string to check.
   * @return true if the string is {@code null} or blank.
   */
  public static boolean isNullOrBlank(String string) {
    return string == null || string.trim().isEmpty();
  }

  /**
   * Is the given string not {@code null} and not blank?
   * @param string The string to check.
   * @return true if there's something in the string.
   */
  public static boolean isNotNullOrBlank(String string) {
    return !isNullOrBlank(string);
  }

  /**
   * Are all the given strings not {@code null} and not blank?
   * @param strings The strings to check.
   * @return {@code true} if every string is non-{@code null} and non-empty.
   */
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

  /**
   * Is the collection {@code null} or empty?
   * @param collection The collection to check.
   * @return {@code true} if the collection is {@code null} or {@link Collection#isEmpty()}, otherwise {@code false}.
   */
  public static boolean isNullOrEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  /**
   * @deprecated Use {@link #isNullOrEmpty(Collection)} instead.
   * @param collection The collection to check.
   * @return {@code true} if the collection is {@code null} or empty, {@code false} otherwise.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static boolean isCollectionEmpty(Collection<?> collection) {
    return isNullOrEmpty(collection);
  }

  /**
   * Returns a string consisting of the given string repeated {@code times} times.
   *
   * @param string The string to repeat.
   * @param times  The number of times to repeat the string.
   * @return A string consisting of the given string repeated {@code times} times.
   */
  public static String repeat(String string, int times) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < times; i++) {
      sb.append(string);
    }
    return sb.toString();
  }

  /**
   * Returns a random UUID.
   * @return a UUID.
   */
  public static String randomUUID() {
    return UUID.randomUUID().toString();
  }

  /**
   * Internal method to get a SHA-256 instance of {@link MessageDigest}.
   * @return a {@link MessageDigest}.
   */
  @JacocoIgnoreCoverageGenerated
  private static MessageDigest getSha256Instance() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Generates a UUID from a hash of the given input string.
   * @param input The input string.
   * @return A UUID.
   */
  public static String generateUUIDFrom(String input) {
      byte[] hashBytes = getSha256Instance().digest(input.getBytes(UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) sb.append(String.format("%02x", b));
      return UUID.nameUUIDFromBytes(sb.toString().getBytes(UTF_8)).toString();
  }

  /**
   * Returns the given object's {@code toString()} surrounded by quotes.
   *
   * <p>If the given object is {@code null}, the string {@code "null"} is returned.
   *
   * @param object The object to quote.
   * @return The given object surrounded by quotes.
   */
  public static String quoted(Object object) {
    if (object == null) {
      return "null";
    }
    return "\"" + object + "\"";
  }

  /**
   * Returns the first {@code numberOfChars} characters of the given string.
   * If the string is shorter than {@code numberOfChars}, the whole string is returned.
   *
   * @param string        The string to get the first characters from.
   * @param numberOfChars The number of characters to return.
   * @return The first {@code numberOfChars} characters of the given string.
   */
  public static String firstChars(String string, int numberOfChars) {
    if (string == null) {
      return null;
    }
    return string.length() > numberOfChars ? string.substring(0, numberOfChars) : string;
  }

  /**
   * Reads the content as bytes from the given URL as a GET request.
   * @param url The URL to read from.
   * @return The content as bytes.
   * @throws RuntimeException if the request fails.
   */
  public static byte[] readBytes(String url) {
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

  /**
   * Returns an (unmodifiable) copy of the provided list.
   * Returns <code>null</code> if the provided list is <code>null</code>.
   *
   * @param list The list to copy.
   * @param <T>  Generic type of the list.
   * @return The copy of the provided list.
   */
  public static <T> List<T> copyIfNotNull(List<T> list) {
    if (list == null) {
      return null;
    }

    return unmodifiableList(list);
  }
}
