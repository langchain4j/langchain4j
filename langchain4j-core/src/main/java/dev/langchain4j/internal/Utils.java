package dev.langchain4j.internal;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

import dev.langchain4j.Internal;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods.
 */
@Internal
public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    private Utils() {}

    /**
     * Returns the first non-null value from the provided array of values.
     * If all values are null, an IllegalArgumentException is thrown.
     *
     * @param name   A non-null string representing the name associated with the values.
     * @param values An array of potentially nullable values to search through.
     * @return The first non-null value in the array.
     * @throws IllegalArgumentException If all values are null or if the array is empty.
     */
    @SafeVarargs
    @NonNull
    public static <T> T firstNotNull(@NonNull String name, @Nullable T... values) {
        ensureNotEmpty(values, name + " values");
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        throw illegalArgument("At least one of the given '%s' values must be not null", name);
    }

    /**
     * Returns the given value if it is not {@code null}, otherwise returns the given default value.
     *
     * @param value        The value to return if it is not {@code null}.
     * @param defaultValue The value to return if the value is {@code null}.
     * @param <T>          The type of the value.
     * @return the given value if it is not {@code null}, otherwise returns the given default value.
     */
    public static <T> T getOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the given list if it is not {@code null} and not empty, otherwise returns the given default list.
     *
     * @param list        The list to return if it is not {@code null} and not empty.
     * @param defaultList The list to return if the list is {@code null} or empty.
     * @param <T>         The type of the value.
     * @return the given list if it is not {@code null} and not empty, otherwise returns the given default list.
     */
    public static <T> List<T> getOrDefault(List<T> list, List<T> defaultList) {
        return isNullOrEmpty(list) ? defaultList : list;
    }

    /**
     * Returns the given map if it is not {@code null} and not empty, otherwise returns the given default map.
     *
     * @param map        The map to return if it is not {@code null} and not empty.
     * @param defaultMap The map to return if the map is {@code null} or empty.
     * @return the given map if it is not {@code null} and not empty, otherwise returns the given default map.
     */
    public static <K, V> Map<K, V> getOrDefault(Map<K, V> map, Map<K, V> defaultMap) {
        return isNullOrEmpty(map) ? defaultMap : map;
    }

    /**
     * Returns the given value if it is not {@code null}, otherwise returns the value returned by the given supplier.
     *
     * @param value                The value to return if it is not {@code null}.
     * @param defaultValueSupplier The supplier to call if the value is {@code null}.
     * @param <T>                  The type of the value.
     * @return the given value if it is not {@code null}, otherwise returns the value returned by the given supplier.
     */
    public static <T> T getOrDefault(@Nullable T value, Supplier<T> defaultValueSupplier) {
        return value != null ? value : defaultValueSupplier.get();
    }

    /**
     * Is the given string {@code null} or blank?
     *
     * @param string The string to check.
     * @return true if the string is {@code null} or blank.
     */
    public static boolean isNullOrBlank(String string) {
        return string == null || string.trim().isEmpty();
    }

    /**
     * Is the given string {@code null} or empty ("")?
     *
     * @param string The string to check.
     * @return true if the string is {@code null} or empty.
     */
    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Is the given string not {@code null} and not blank?
     *
     * @param string The string to check.
     * @return true if there's something in the string.
     */
    public static boolean isNotNullOrBlank(String string) {
        return !isNullOrBlank(string);
    }

    /**
     * Is the given string not {@code null} and not empty ("")?
     *
     * @param string The string to check.
     * @return true if the given string is not {@code null} and not empty ("")?
     */
    public static boolean isNotNullOrEmpty(String string) {
        return !isNullOrEmpty(string);
    }

    /**
     * Are all the given strings not {@code null} and not blank?
     *
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
     *
     * @param collection The collection to check.
     * @return {@code true} if the collection is {@code null} or {@link Collection#isEmpty()}, otherwise {@code false}.
     */
    public static boolean isNullOrEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Is the iterable object {@code null} or empty?
     *
     * @param iterable The iterable object to check.
     * @return {@code true} if the iterable object is {@code null} or there are no objects to iterate over, otherwise {@code false}.
     */
    public static boolean isNullOrEmpty(Iterable<?> iterable) {
        return iterable == null || !iterable.iterator().hasNext();
    }

    /**
     * Utility method to check if an array is null or has no elements.
     *
     * @param array the array to check
     * @return {@code true} if the array is null or has no elements, otherwise {@code false}
     */
    public static boolean isNullOrEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Is the map object {@code null} or empty?
     *
     * @param map The iterable object to check.
     * @return {@code true} if the map object is {@code null} or empty map, otherwise {@code false}.
     */
    public static boolean isNullOrEmpty(@Nullable Map<?, ?> map) {
        return map == null || map.isEmpty();
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
     *
     * @return a UUID.
     */
    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Internal method to get a SHA-256 instance of {@link MessageDigest}.
     *
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
     *
     * @param input The input string.
     * @return A UUID.
     */
    public static String generateUUIDFrom(String input) {
        byte[] hashBytes = getSha256Instance().digest(input.getBytes(UTF_8));
        String hexFormat = HexFormat.of().formatHex(hashBytes);
        return UUID.nameUUIDFromBytes(hexFormat.getBytes(UTF_8)).toString();
    }

    /**
     * Appends a trailing '/' if the provided URL does not end with '/'
     *
     * @param url URL to check for trailing '/'
     * @return Same URL if it already ends with '/' or a new URL with '/' appended
     */
    public static String ensureTrailingForwardSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
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
     * Reads the content as bytes from the given URL as a GET request for HTTP/HTTPS resources,
     * and from files stored on the local filesystem.
     *
     * @param url The URL to read from.
     * @return The content as bytes.
     * @throws RuntimeException if the request fails.
     */
    public static byte[] readBytes(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // Handle URLs
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                // Add headers to appear as a legitimate browser request
                connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setRequestProperty(
                        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
                connection.setRequestProperty("Connection", "keep-alive");

                int responseCode = connection.getResponseCode();

                if (responseCode == HTTP_OK) {
                    try (InputStream inputStream = connection.getInputStream();
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        return outputStream.toByteArray();
                    } finally {
                        connection.disconnect();
                    }
                } else {
                    throw new RuntimeException("Error while reading: " + responseCode);
                }
            } else {
                // Handle files
                return Files.readAllBytes(Path.of(new URI(url)));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an (unmodifiable) copy of the provided set.
     * Returns <code>null</code> if the provided set is <code>null</code>.
     *
     * @param set The set to copy.
     * @param <T> Generic type of the set.
     * @return The copy of the provided set.
     */
    public static <T> Set<T> copyIfNotNull(Set<T> set) {
        if (set == null) {
            return null;
        }

        return unmodifiableSet(set);
    }

    /**
     * Returns an (unmodifiable) copy of the provided set.
     * Returns an empty set if the provided set is <code>null</code>.
     *
     * @param set The set to copy.
     * @param <T> Generic type of the set.
     * @return The copy of the provided set or an empty set.
     */
    public static <T> Set<T> copy(Set<T> set) {
        if (set == null) {
            return Set.of();
        }

        return unmodifiableSet(set);
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

    /**
     * Returns an (unmodifiable) copy of the provided list.
     * Returns an empty list if the provided list is <code>null</code>.
     *
     * @param list The list to copy.
     * @param <T>  Generic type of the list.
     * @return The copy of the provided list or an empty list.
     */
    public static <T> List<T> copy(List<T> list) {
        if (list == null) {
            return List.of();
        }

        return unmodifiableList(list);
    }

    /**
     * Returns a mutable copy of the provided list.
     * Returns an empty list if the provided list is <code>null</code>.
     *
     * @param list The list to copy.
     * @param <T>  Generic type of the list.
     * @return The copy of the provided list or an empty list.
     */
    public static <T> List<T> mutableCopy(List<T> list) {
        if (list == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(list);
    }

    /**
     * Returns an (unmodifiable) copy of the provided map.
     * Returns <code>null</code> if the provided map is <code>null</code>.
     *
     * @param map The map to copy.
     * @return The copy of the provided map.
     */
    public static <K, V> Map<K, V> copyIfNotNull(Map<K, V> map) {
        if (map == null) {
            return null;
        }

        return unmodifiableMap(map);
    }

    /**
     * Returns an (unmodifiable) copy of the provided map.
     * Returns an empty map if the provided map is <code>null</code>.
     *
     * @param map The map to copy.
     * @return The copy of the provided map or an empty map.
     */
    public static <K, V> Map<K, V> copy(Map<K, V> map) {
        if (map == null) {
            return Map.of();
        }

        return unmodifiableMap(map);
    }

    /**
     * Returns a mutable copy of the provided map.
     * Returns an empty map if the provided map is <code>null</code>.
     *
     * @param map The map to copy.
     * @return The copy of the provided map or an empty map.
     */
    public static <K, V> Map<K, V> mutableCopy(Map<K, V> map) {
        if (map == null) {
            return new HashMap<>();
        }

        return new HashMap<>(map);
    }

    public static Map<String, String> toStringValueMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        Map<String, String> stringValueMap = new HashMap<>();
        for (String key : map.keySet()) {
            Object value = map.get(key);
            String stringValue = Objects.toString(value, null);
            stringValueMap.put(key, stringValue);
        }
        return stringValueMap;
    }

    /**
     * Returns the method eventually annotated with the given annotation.
     * It could be the method itself or, if the method belongs to a proxy,
     * a method from one of the interfaces implemented by the proxy.
     *
     * @param method     The method to check for the annotation.
     * @param annotation The annotation to look for.
     * @return An {@link Optional} containing the method having the given annotation,
     * or an empty {@link Optional} if there isn't any.
     */
    public static Optional<Method> getAnnotatedMethod(Method method, Class<? extends Annotation> annotation) {
        if (method.isAnnotationPresent(annotation)) {
            return Optional.of(method);
        }

        if (Proxy.isProxyClass(method.getDeclaringClass())) {
            for (Class<?> iface : method.getDeclaringClass().getInterfaces()) {
                try {
                    Method interfaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                    if (interfaceMethod.isAnnotationPresent(annotation)) {
                        return Optional.of(interfaceMethod);
                    }
                } catch (NoSuchMethodException e) {
                    // Ignore and continue searching in the next interface
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Logs a warning if the given string value is {@code null} or blank.
     * <p>
     * This method is typically used in constructors or validation routines to
     * highlight missing or incomplete field values without throwing an exception.
     * It returns the original value unchanged so it can be assigned directly to
     * a field or variable.
     * <p>
     * Example usage:
     * <pre>
     * this.name = Utils.warnIfNullOrBlank(name, "name", McpResource.class);
     * </pre>
     *
     * Which will log:
     * <pre>
     * McpResource: 'name' is null or blank
     * </pre>
     *
     * @param value     the string value to check (may be {@code null} or blank)
     * @param fieldName the name of the field being validated (used in the log message)
     * @param clazz     the class where the validation occurs (used to identify context in the log)
     * @return the original value (may be {@code null} or blank)
     */
    public static String warnIfNullOrBlank(String value, String fieldName, Class<?> clazz) {
        if (isNullOrBlank(value)) {
            log.warn("{}: '{}' is null or blank", clazz.getSimpleName(), fieldName);
        }
        return value;
    }
}
