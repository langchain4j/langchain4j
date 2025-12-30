package dev.langchain4j.store.embedding.oceanbase;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

class OceanBaseMetadataFilterMapper {

    /**
     * Maps a Filter to SQL WHERE clause expression.
     * Supports filtering by table columns (id, text, metadata, vector) and metadata fields.
     * 
     * @param filter The filter to convert
     * @param fieldDefinition Field definition containing table column names
     * @return SQL WHERE clause expression
     */
    static String map(Filter filter, FieldDefinition fieldDefinition) {
        if (filter instanceof ContainsString containsString) {
            return mapContains(containsString, fieldDefinition);
        } else if (filter instanceof IsEqualTo isEqualTo) {
            return mapEqual(isEqualTo, fieldDefinition);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mapNotEqual(isNotEqualTo, fieldDefinition);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return mapGreaterThan(isGreaterThan, fieldDefinition);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual(isGreaterThanOrEqualTo, fieldDefinition);
        } else if (filter instanceof IsLessThan isLessThan) {
            return mapLessThan(isLessThan, fieldDefinition);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return mapLessThanOrEqual(isLessThanOrEqualTo, fieldDefinition);
        } else if (filter instanceof IsIn isIn) {
            return mapIn(isIn, fieldDefinition);
        } else if (filter instanceof IsNotIn isNotIn) {
            return mapNotIn(isNotIn, fieldDefinition);
        } else if (filter instanceof And and) {
            return mapAnd(and, fieldDefinition);
        } else if (filter instanceof Not not) {
            return mapNot(not, fieldDefinition);
        } else if (filter instanceof Or or) {
            return mapOr(or, fieldDefinition);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    /**
     * Converts a filter key to SQL expression.
     * If the key matches a table column name, returns the column name directly.
     * Otherwise, returns JSON_EXTRACT expression for metadata field access.
     * 
     * @param key The filter key
     * @param fieldDefinition Field definition containing table column names
     * @return SQL expression for the key
     */
    private static String convertKey(String key, FieldDefinition fieldDefinition) {
        String keyLower = key.toLowerCase();
        // Check if key matches table column names (case-insensitive)
        if (keyLower.equals(fieldDefinition.getIdFieldName().toLowerCase())) {
            return fieldDefinition.getIdFieldName();
        } else if (keyLower.equals(fieldDefinition.getTextFieldName().toLowerCase()) 
                || keyLower.equals("document") || keyLower.equals("text")) {
            return fieldDefinition.getTextFieldName();
        } else if (keyLower.equals(fieldDefinition.getMetadataFieldName().toLowerCase()) 
                || keyLower.equals("metadata")) {
            return fieldDefinition.getMetadataFieldName();
        } else if (keyLower.equals(fieldDefinition.getVectorFieldName().toLowerCase()) 
                || keyLower.equals("vector") || keyLower.equals("embedding")) {
            return fieldDefinition.getVectorFieldName();
        } else {
            // Metadata field - use JSON_EXTRACT
            // Return the expression that will be used for comparison
            return format("JSON_UNQUOTE(JSON_EXTRACT(%s, '$.%s'))", 
                    fieldDefinition.getMetadataFieldName(), key);
        }
    }

    private static String mapContains(ContainsString containsString, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(containsString.key(), fieldDefinition);
        return format("%s LIKE %s", keyExpr, formatValue("%" + containsString.comparisonValue() + "%"));
    }

    private static String mapEqual(IsEqualTo isEqualTo, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isEqualTo.key(), fieldDefinition);
        return format("%s = %s", keyExpr, formatValue(isEqualTo.comparisonValue()));
    }

    private static String mapNotEqual(IsNotEqualTo isNotEqualTo, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isNotEqualTo.key(), fieldDefinition);
        // For metadata fields, NOT EQUAL should also include NULL values
        // (when the field doesn't exist in metadata)
        if (keyExpr.contains("JSON_EXTRACT")) {
            String jsonExtractExpr = extractJsonExtractExpression(keyExpr);
            // NOT EQUAL should match: (field != value OR JSON_EXTRACT IS NULL)
            return format("(%s != %s OR %s IS NULL)", 
                keyExpr, formatValue(isNotEqualTo.comparisonValue()), jsonExtractExpr);
        }
        // For non-metadata fields, use standard NOT EQUAL
        return format("%s != %s", keyExpr, formatValue(isNotEqualTo.comparisonValue()));
    }

    private static String mapGreaterThan(IsGreaterThan isGreaterThan, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isGreaterThan.key(), fieldDefinition);
        return format("%s > %s", keyExpr, formatValue(isGreaterThan.comparisonValue()));
    }

    private static String mapGreaterThanOrEqual(
            IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isGreaterThanOrEqualTo.key(), fieldDefinition);
        return format("%s >= %s", keyExpr, formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private static String mapLessThan(IsLessThan isLessThan, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isLessThan.key(), fieldDefinition);
        return format("%s < %s", keyExpr, formatValue(isLessThan.comparisonValue()));
    }

    private static String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isLessThanOrEqualTo.key(), fieldDefinition);
        return format("%s <= %s", keyExpr, formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private static String mapIn(IsIn isIn, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isIn.key(), fieldDefinition);
        Collection<?> valuesCollection = isIn.comparisonValues();
        List<?> values = valuesCollection instanceof List ? (List<?>) valuesCollection : new ArrayList<>(valuesCollection);
        if (values.isEmpty()) {
            return "1=0"; // Always false
        }
        // Convert IN to multiple OR conditions (same approach as reference project)
        // This works reliably for both table columns and JSON_EXTRACT results
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            conditions.add(format("%s = %s", keyExpr, formatValue(values.get(i))));
        }
        return "(" + String.join(" OR ", conditions) + ")";
    }

    private static String mapNotIn(IsNotIn isNotIn, FieldDefinition fieldDefinition) {
        String keyExpr = convertKey(isNotIn.key(), fieldDefinition);
        Collection<?> valuesCollection = isNotIn.comparisonValues();
        List<?> values = valuesCollection instanceof List ? (List<?>) valuesCollection : new ArrayList<>(valuesCollection);
        if (values.isEmpty()) {
            return "1=1"; // Always true
        }
        // For metadata fields, NOT IN should also include NULL values
        if (keyExpr.contains("JSON_EXTRACT")) {
            String jsonExtractExpr = extractJsonExtractExpression(keyExpr);
            // Convert NOT IN to NOT (OR conditions) with NULL check
            List<String> conditions = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                conditions.add(format("%s = %s", keyExpr, formatValue(values.get(i))));
            }
            return format("(NOT (%s) OR %s IS NULL)", 
                String.join(" OR ", conditions), jsonExtractExpr);
        }
        // For non-metadata fields, use standard NOT IN
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            conditions.add(format("%s = %s", keyExpr, formatValue(values.get(i))));
        }
        return "NOT (" + String.join(" OR ", conditions) + ")";
    }


    private static String mapAnd(And and, FieldDefinition fieldDefinition) {
        return format("(%s AND %s)", map(and.left(), fieldDefinition), map(and.right(), fieldDefinition));
    }

    private static String mapNot(Not not, FieldDefinition fieldDefinition) {
        Filter innerFilter = not.expression();
        
        // For metadata fields, NOT should also include NULL values
        // (when the field doesn't exist in metadata)
        // Extract the key expression if it's a comparison filter
        String keyExpr = extractKeyExpression(innerFilter, fieldDefinition);
        if (keyExpr != null && keyExpr.contains("JSON_EXTRACT")) {
            // Extract the JSON_EXTRACT expression itself (before JSON_UNQUOTE)
            // This is needed to check if the field exists in JSON
            String jsonExtractExpr = extractJsonExtractExpression(keyExpr);
            
            // For metadata fields, we need to handle NULL values explicitly
            // Convert NOT filter to equivalent form that includes NULL check
            if (innerFilter instanceof IsEqualTo isEqualTo) {
                // NOT (field = value) should be: (field != value OR JSON_EXTRACT IS NULL)
                // When field doesn't exist, JSON_EXTRACT returns NULL
                return format("(%s != %s OR %s IS NULL)", 
                    keyExpr, formatValue(isEqualTo.comparisonValue()), jsonExtractExpr);
            } else if (innerFilter instanceof ContainsString containsString) {
                // NOT (field LIKE '%value%') should be: (field NOT LIKE '%value%' OR JSON_EXTRACT IS NULL)
                return format("(%s NOT LIKE %s OR %s IS NULL)", 
                    keyExpr, formatValue("%" + containsString.comparisonValue() + "%"), jsonExtractExpr);
            } else if (innerFilter instanceof IsIn isIn) {
                // NOT (field IN (v1, v2, ...)) should be: (field NOT IN (v1, v2, ...) OR JSON_EXTRACT IS NULL)
                Collection<?> valuesCollection = isIn.comparisonValues();
                List<?> values = valuesCollection instanceof List ? (List<?>) valuesCollection : new ArrayList<>(valuesCollection);
                if (values.isEmpty()) {
                    return format("(%s IS NOT NULL)", jsonExtractExpr); // NOT IN empty set means field exists
                }
                List<String> valueStrings = new ArrayList<>();
                for (Object value : values) {
                    valueStrings.add(formatValue(value));
                }
                return format("(%s NOT IN (%s) OR %s IS NULL)", 
                    keyExpr, String.join(", ", valueStrings), jsonExtractExpr);
            } else {
                // For other comparison filters, use NOT with NULL check
                String innerExpr = map(innerFilter, fieldDefinition);
                return format("(NOT (%s) OR %s IS NULL)", innerExpr, jsonExtractExpr);
            }
        }
        
        // For non-metadata fields, use standard NOT
        String innerExpr = map(innerFilter, fieldDefinition);
        return format("NOT (%s)", innerExpr);
    }
    
    /**
     * Extracts the JSON_EXTRACT expression from a JSON_UNQUOTE(JSON_EXTRACT(...)) expression.
     * For example: "JSON_UNQUOTE(JSON_EXTRACT(metadata_field, '$.name'))" -> "JSON_EXTRACT(metadata_field, '$.name')"
     */
    private static String extractJsonExtractExpression(String keyExpr) {
        // If keyExpr is JSON_UNQUOTE(JSON_EXTRACT(...)), extract the JSON_EXTRACT part
        if (keyExpr.startsWith("JSON_UNQUOTE(") && keyExpr.endsWith(")")) {
            String inner = keyExpr.substring("JSON_UNQUOTE(".length(), keyExpr.length() - 1);
            if (inner.startsWith("JSON_EXTRACT(")) {
                return inner;
            }
        }
        // If it's already JSON_EXTRACT, return as is
        if (keyExpr.startsWith("JSON_EXTRACT(")) {
            return keyExpr;
        }
        // Fallback: return the original expression
        return keyExpr;
    }
    
    /**
     * Extracts the key expression from a filter if it's a comparison filter.
     * Returns null if the filter doesn't have a single key.
     */
    private static String extractKeyExpression(Filter filter, FieldDefinition fieldDefinition) {
        if (filter instanceof ContainsString containsString) {
            return convertKey(containsString.key(), fieldDefinition);
        } else if (filter instanceof IsEqualTo isEqualTo) {
            return convertKey(isEqualTo.key(), fieldDefinition);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return convertKey(isNotEqualTo.key(), fieldDefinition);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return convertKey(isGreaterThan.key(), fieldDefinition);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return convertKey(isGreaterThanOrEqualTo.key(), fieldDefinition);
        } else if (filter instanceof IsLessThan isLessThan) {
            return convertKey(isLessThan.key(), fieldDefinition);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return convertKey(isLessThanOrEqualTo.key(), fieldDefinition);
        } else if (filter instanceof IsIn isIn) {
            return convertKey(isIn.key(), fieldDefinition);
        } else if (filter instanceof IsNotIn isNotIn) {
            return convertKey(isNotIn.key(), fieldDefinition);
        }
        // For complex filters (And, Or, Not), we can't extract a single key
        return null;
    }

    private static String mapOr(Or or, FieldDefinition fieldDefinition) {
        return format("(%s OR %s)", map(or.left(), fieldDefinition), map(or.right(), fieldDefinition));
    }

    private static String formatValue(Object value) {
        if (value instanceof String stringValue) {
            // Escape single quotes by replacing them with ''
            final String escapedValue = stringValue.replace("'", "''");
            return "'" + escapedValue + "'";
        } else if (value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    protected static List<String> formatValues(Collection<?> values) {
        return values.stream().map(OceanBaseMetadataFilterMapper::formatValue).collect(toList());
    }
}

