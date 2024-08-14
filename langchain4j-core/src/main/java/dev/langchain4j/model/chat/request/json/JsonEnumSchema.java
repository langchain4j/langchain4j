package dev.langchain4j.model.chat.request.json;

import com.google.gson.annotations.SerializedName;
import dev.langchain4j.Experimental;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

@Experimental
public class JsonEnumSchema implements JsonSchemaElement {

    private final String description;
    @SerializedName("enum")
    private final List<String> enumValues;

    public JsonEnumSchema(Builder builder) {
        this.description = builder.description;
        this.enumValues = new ArrayList<>(ensureNotEmpty(builder.enumValues, "enumValues"));
    }

    public String description() {
        return description;
    }

    public List<String> enumValues() {
        return enumValues;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private List<String> enumValues;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder enumValues(List<String> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public Builder enumValues(String... enumValues) {
            return enumValues(asList(enumValues));
        }

        public Builder enumValues(Class<?> enumClass) {
            if (!enumClass.isEnum()) {
                throw new RuntimeException("Class " + enumClass.getName() + " must be enum");
            }

            List<String> enumValues = stream(enumClass.getEnumConstants())
                    .map(Object::toString)
                    .collect(toList());

            return enumValues(enumValues);
        }

        public JsonEnumSchema build() {
            return new JsonEnumSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonEnumSchema that = (JsonEnumSchema) o;
        return Objects.equals(this.description, that.description)
                && Objects.equals(this.enumValues, that.enumValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, enumValues);
    }

    @Override
    public String toString() {
        return "JsonEnumSchema {" +
                "description = " + quoted(description) +
                ", enumValues = " + enumValues +
                " }";
    }
}
