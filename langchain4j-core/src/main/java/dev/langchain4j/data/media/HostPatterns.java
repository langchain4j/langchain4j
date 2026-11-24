package dev.langchain4j.data.media;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

import java.net.IDN;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A list of host names, each either exact ({@code images.example.com}) or a wildcard for every subdomain
 * ({@code *.example.com}, which does not match {@code example.com} itself). Matching ignores case, a trailing dot and
 * the brackets of IPv6 literals. Host names with non-ASCII characters are converted to their ASCII form
 * ({@code xn--...}), the only form that {@code URI.getHost()} returns.
 */
final class HostPatterns {

    private static final HostPatterns NONE = new HostPatterns(Set.of(), List.of());

    private final Set<String> exactHosts;
    private final List<String> domainSuffixes;

    private HostPatterns(Set<String> exactHosts, List<String> domainSuffixes) {
        this.exactHosts = exactHosts;
        this.domainSuffixes = domainSuffixes;
    }

    static HostPatterns none() {
        return NONE;
    }

    static HostPatterns from(Collection<String> patterns, String optionName) {
        Set<String> exactHosts = new HashSet<>();
        List<String> domainSuffixes = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern == null) {
                throw illegalArgument("%s must not contain null", optionName);
            }
            String normalized = normalize(pattern);
            if (normalized.startsWith("*.")) {
                String domain = toAscii(normalized.substring(2), pattern, optionName);
                ensureValidHost(domain, pattern, optionName);
                domainSuffixes.add("." + domain);
            } else {
                String host = toAscii(normalized, pattern, optionName);
                ensureValidHost(host, pattern, optionName);
                exactHosts.add(host);
            }
        }
        return new HostPatterns(Set.copyOf(exactHosts), List.copyOf(domainSuffixes));
    }

    private static String toAscii(String host, String pattern, String optionName) {
        if (host.chars().allMatch(c -> c < 0x80)) {
            return host;
        }
        try {
            return IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw invalidHost(pattern, optionName);
        }
    }

    private static void ensureValidHost(String host, String pattern, String optionName) {
        boolean hasPort = host.indexOf(':') >= 0 && host.indexOf(':') == host.lastIndexOf(':');
        if (host.isEmpty()
                || hasPort
                || host.chars()
                        .anyMatch(c -> c == '*'
                                || c == '/'
                                || c == '@'
                                || c == '['
                                || c == ']'
                                || Character.isWhitespace(c))) {
            throw invalidHost(pattern, optionName);
        }
    }

    private static IllegalArgumentException invalidHost(String pattern, String optionName) {
        return illegalArgument(
                "%s must contain host names such as \"images.example.com\" or \"*.example.com\""
                        + " (no scheme, port or path), but contains \"%s\"",
                optionName, pattern);
    }

    boolean matches(String normalizedHost) {
        if (exactHosts.contains(normalizedHost)) {
            return true;
        }
        for (String domainSuffix : domainSuffixes) {
            if (normalizedHost.endsWith(domainSuffix)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
