package dev.langchain4j.internal;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class UnquotedEnumPreprocessor {

    // 1) Matches something like [CAT, DOG, Bird] (tokens can be letters/numbers/underscores)
    //    capturing the inside as group(1). We'll insert quotes around each token.
    private static final Pattern UNQUOTED_ENUM_ARRAY = Pattern.compile(
            "\\[\\s*([A-Za-z0-9_]+\\s*(?:,\\s*[A-Za-z0-9_]+\\s*)*)\\]"
    );


    // 2) Matches unquoted property values like  :CAT or :cat or :Dog
    //    capturing the colon + whitespace as group(1), the token as group(2),
    //    the trailing whitespace (if any) as group(3), and ensures it's followed by } , or end-of-string.
    private static final Pattern UNQUOTED_VALUE_PATTERN = Pattern.compile(
            "(:\\s*)([A-Za-z0-9_]+)(\\s*)(?=[},]|$)"
    );

    /**
     * Combines both "fix array" and "fix property" steps:
     */
    public static String quoteUnquotedTokens(String input) {
        if (input == null) {
            return null;
        }

        // Step 1: fix array values like [CAT, Dog] -> ["CAT","DOG"]
        String step1 = fixUnquotedArrayValues(input);

        // Step 2: fix unquoted property values like "animal":CAT -> "animal":"CAT"
        String step2 = fixUnquotedObjectValues(step1);

        return step2;
    }

    /**
     * Step 1: Convert [CAT, Dog] → ["CAT","DOG"]
     */
    private static String fixUnquotedArrayValues(String input) {
        Matcher matcher = UNQUOTED_ENUM_ARRAY.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            // e.g. group(1) = "CAT, Dog"
            String inside = matcher.group(1);
            String[] tokens = inside.split(",");
            String quotedTokens = Arrays.stream(tokens)
                    .map(String::trim)
                    // optional: unify to uppercase if your enums are uppercase
                    .map(t -> t.toUpperCase())
                    .map(t -> "\"" + t + "\"")
                    .collect(Collectors.joining(","));
            // Rebuild bracketed array: ["CAT","DOG"]
            String replacement = "[" + quotedTokens + "]";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Step 2: Convert {... "animal":CAT ...} → {... "animal":"CAT" ...}
     */
    private static String fixUnquotedObjectValues(String input) {
        Matcher matcher = UNQUOTED_VALUE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String prefix = matcher.group(1);  // the colon + spaces
            String token  = matcher.group(2);  // the unquoted token
            String space  = matcher.group(3);

            String replacement;
            // Leave null/true/false/numbers unquoted.
            // Only uppercase/quote tokens that are presumably enum names:
            if ("null".equalsIgnoreCase(token)
                    || "true".equalsIgnoreCase(token)
                    || "false".equalsIgnoreCase(token)
                    || token.matches("\\d+")) {
                // Don’t quote these—leave them as is.
                replacement = prefix + token + space;
            } else {
                // Otherwise treat it like an enum, uppercase and quote it:
                replacement = prefix + "\"" + token.toUpperCase() + "\"" + space;
            }
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}
