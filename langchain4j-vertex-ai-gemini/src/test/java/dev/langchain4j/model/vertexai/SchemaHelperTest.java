package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import lombok.Data;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static dev.langchain4j.model.vertexai.SchemaHelper.fromClass;
import static org.assertj.core.api.Assertions.assertThat;

public class SchemaHelperTest {

    @Test
    void should_convert_class_into_schema() {

        // given
        class Person {
            public String name;
            public int age;
            public boolean isStudent;
            public String[] friends;
        }

        // when
        Schema schema = fromClass(Person.class);

        // then
        assertThat(schema.getRequiredList()).contains("name", "age", "isStudent", "friends");
        assertThat(schema.getPropertiesMap().keySet()).contains("name", "age", "isStudent", "friends");
        assertThat(schema.getPropertiesMap().get("name").getType()).isEqualTo(Type.STRING);
        assertThat(schema.getPropertiesMap().get("age").getType()).isEqualTo(Type.INTEGER);
        assertThat(schema.getPropertiesMap().get("isStudent").getType()).isEqualTo(Type.BOOLEAN);
        assertThat(schema.getPropertiesMap().get("friends").getType()).isEqualTo(Type.ARRAY);
        assertThat(schema.getPropertiesMap().get("friends").getItems().getType()).isEqualTo(Type.STRING);
    }

    @Test
    void should_convert_json_schema_string_into_schema() {

        // when
        Schema schema = SchemaHelper.fromJsonSchema("{\n" +
            "  \"type\": \"OBJECT\",\n" +
            "  \"properties\": {\n" +
            "    \"artist-name\": {\n" +
            "      \"type\": \"STRING\"\n" +
            "    },\n" +
            "    \"artist-age\": {\n" +
            "      \"type\": \"INTEGER\"\n" +
            "    },\n" +
            "    \"artist-adult\": {\n" +
            "      \"type\": \"BOOLEAN\"\n" +
            "    },\n" +
            "    \"artist-pets\": {\n" +
            "      \"type\": \"ARRAY\",\n" +
            "      \"items\": \n" +
            "        {\n" +
            "          \"type\": \"STRING\"\n" +
            "        }\n" +
            "    },\n" +
            "    \"artist-address\": {\n" +
            "      \"type\": \"STRING\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"required\": [\n" +
            "    \"artist-name\",\n" +
            "    \"artist-age\",\n" +
            "    \"artist-adult\",\n" +
            "    \"artist-pets\",\n" +
            "    \"artist-address\"\n" +
            "  ]\n" +
            "}");

        // then
        assertThat(schema.getRequiredList()).contains("artist-name", "artist-age", "artist-adult", "artist-pets", "artist-address");
        assertThat(schema.getPropertiesMap().keySet()).contains("artist-name", "artist-age", "artist-adult", "artist-pets", "artist-address");
        assertThat(schema.getPropertiesMap().get("artist-name").getType()).isEqualTo(Type.STRING);
        assertThat(schema.getPropertiesMap().get("artist-address").getType()).isEqualTo(Type.STRING);
        assertThat(schema.getPropertiesMap().get("artist-age").getType()).isEqualTo(Type.INTEGER);
        assertThat(schema.getPropertiesMap().get("artist-adult").getType()).isEqualTo(Type.BOOLEAN);
        assertThat(schema.getPropertiesMap().get("artist-pets").getType()).isEqualTo(Type.ARRAY);
        assertThat(schema.getPropertiesMap().get("artist-pets").getItems().getType()).isEqualTo(Type.STRING);
    }

    @Getter
    enum Sentiment {
        POSITIVE(1.0), NEUTRAL(0.0), NEGATIVE(-1.0);
        private final double value;
        Sentiment(double val) {
            this.value = val;
        }
    }

    @Data
    static class SentimentClassification {
        private Sentiment sentiment;
    }

    @Test
    void should_support_enum_schema_without_stackoverflow() {

        // given
        Schema schemaFromEnum = fromClass(SentimentClassification.class);

        Schema expectedSchema = Schema.newBuilder()
            .setType(Type.OBJECT)
            .putProperties("sentiment", Schema.newBuilder()
                .setType(Type.STRING)
                .addAllEnum(Arrays.asList("POSITIVE", "NEUTRAL", "NEGATIVE"))
                .build())
            .addAllRequired(Collections.singletonList("sentiment"))
            .build();

        // then
        assertThat(schemaFromEnum).isEqualTo(expectedSchema);
    }
}
