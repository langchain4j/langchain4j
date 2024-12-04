package dev.langchain4j.store.embedding.mariadb;

import dev.langchain4j.internal.ValidationUtils;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MetadataColumDefinition used to define column definition from sql String
 */
public record MetadataColumDefinition(String fullDefinition, String escapedName, String name, String type) {
    private static final Pattern litteralPattern =
            Pattern.compile("^(([a-zA-Z0-9_]+)|(`((``)|[^`])+`))", Pattern.DOTALL);

    /**
     * transform sql string to MetadataColumDefinition
     * @param sqlDefinition sql definition string
     * @param sqlKeywords sql reserved keywords
     * @return MetadataColumDefinition
     */
    public static MetadataColumDefinition from(String sqlDefinition, List<String> sqlKeywords) {
        String fullDefinition = ValidationUtils.ensureNotNull(sqlDefinition, "Metadata column definition")
                .trim();
        Matcher matcher = litteralPattern.matcher(sqlDefinition);
        if (matcher.find()) {
            String fieldName = matcher.group(0);
            String remainingDefinition =
                    fullDefinition.substring(fieldName.length()).trim();
            if (remainingDefinition.isEmpty()) {
                throw new IllegalArgumentException("Definition format should be: <column name> <type> "
                        + " [ NULL | NOT NULL ] [ UNIQUE ] [ DEFAULT value ]");
            }
            String escapedName = fieldName;
            String unescapedName =
                    (fieldName.startsWith("`")) ? fieldName.substring(1, fieldName.length() - 1) : fieldName;
            String type = fullDefinition
                    .substring(fieldName.length())
                    .trim()
                    .split(" ")[0]
                    .toLowerCase();

            if (!fieldName.startsWith("`") && sqlKeywords.contains(unescapedName.toLowerCase(Locale.ROOT))) {
                // if field name is a reserved keywords, force quote
                escapedName = MariaDbValidator.validateAndEnquoteIdentifier(unescapedName, true);
                fullDefinition = escapedName + fullDefinition.substring(fieldName.length());
            }
            return new MetadataColumDefinition(fullDefinition, escapedName, unescapedName, type);
        } else
            throw new IllegalArgumentException("Wrong definition format should be: <column name> <type> "
                    + " [ NULL | NOT NULL ] [ UNIQUE ] [ DEFAULT value ]");
    }
}
