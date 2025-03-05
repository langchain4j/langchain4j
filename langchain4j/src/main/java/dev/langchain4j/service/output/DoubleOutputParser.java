package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.Map;

class DoubleOutputParser implements OutputParser<Double> {

    @Override
    public Double parse(String text) {
        // 1. null or blank => throw
        if (text == null || text.isBlank()) {
            throw new NumberFormatException("No data to parse");
        }

        String trimmed = text.trim();

        // 2. Check if JSON
        if (trimmed.startsWith("{")) {
            Map<?, ?> map = Json.fromJson(trimmed, Map.class);
            if (map == null || map.isEmpty()) {
                throw new NumberFormatException("No data to parse");
            }

            // a) Check for "value" key or fallback to first property
            Object value;
            if (map.containsKey("value")) {
                value = map.get("value");
            } else {
                value = map.values().iterator().next();
            }

            // b) If null => throw
            if (value == null) {
                throw new NumberFormatException("No data to parse");
            }

            // c) Parse strictly as double
            return parseStrictDouble(value.toString().trim());
        }

        // 3. Plain text => parse as double
        return parseStrictDouble(trimmed);
    }

    private Double parseStrictDouble(String val) {
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Could not parse double from: " + val);
        }
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
