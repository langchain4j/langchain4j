package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.Internal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Hides credentials in what LangChain4j writes to logs and exception messages.
 * <p>
 * When in doubt, it masks: a value that was masked without need costs one debugging round trip,
 * an unmasked credential costs a key rotation.
 */
@Internal
public final class SecretMasking {

    private static final List<String> SECRET_NAME_KEYWORDS =
            List.of("auth", "key", "token", "secret", "password", "credential", "cookie", "signature", "session");

    private static final int MIN_LENGTH_TO_SHOW_PARTIALLY = 20;

    /**
     * The user info of a URL: everything up to the last {@code @} before the first {@code /} after the scheme.
     * Taking the last {@code @}, and not stopping at {@code ?} or {@code #}, also covers passwords
     * that contain these characters without being percent-encoded.
     */
    private static final Pattern USER_INFO = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*://)?[^/]*@");

    private SecretMasking() {}

    /**
     * Returns {@code true} if a header, query parameter or other value with this name probably carries a credential:
     * its name contains {@code auth}, {@code key}, {@code token}, {@code secret}, {@code password},
     * {@code credential}, {@code cookie}, {@code signature} or {@code session}, ignoring case.
     */
    public static boolean isSecretName(String name) {
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        return SECRET_NAME_KEYWORDS.stream().anyMatch(lowerCaseName::contains);
    }

    /**
     * Masks a credential: a value of 20 or more characters is shown as its first 5 and last 2 characters
     * ({@code sk-pr...Xy}), a shorter value is replaced by {@code ...}.
     *
     * @return the masked value, or the value itself if it is {@code null} or blank
     */
    public static @Nullable String mask(@Nullable String value) {
        if (isNullOrBlank(value)) {
            return value;
        }
        if (value.length() >= MIN_LENGTH_TO_SHOW_PARTIALLY) {
            return value.substring(0, 5) + "..." + value.substring(value.length() - 2);
        }
        return "...";
    }

    /**
     * Replaces the user info of a URL with {@code ***}: {@code https://user:password@host/path} becomes
     * {@code https://***@host/path}. The rest of the URL is kept.
     */
    public static String maskUserInfo(String url) {
        return replaceUserInfo(url, "***@");
    }

    /**
     * Removes the user info, the query and the fragment of a URL, as all of them can carry credentials:
     * {@code https://user:password@host/path?key=secret} becomes {@code https://host/path}.
     * Also works for malformed URLs.
     *
     * @return the URL without credentials, or {@code null} if the URL is {@code null}
     */
    public static @Nullable String withoutCredentials(@Nullable String url) {
        if (url == null) {
            return null;
        }
        return replaceUserInfo(url, "").split("[?#]", 2)[0];
    }

    private static String replaceUserInfo(String url, String replacement) {
        Matcher matcher = USER_INFO.matcher(url);
        if (!matcher.lookingAt()) {
            return url;
        }
        String scheme = matcher.group(1) == null ? "" : matcher.group(1);
        return scheme + replacement + url.substring(matcher.end());
    }
}
