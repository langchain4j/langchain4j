package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class PojoOutputParserTest {

    static class PojoWithTransientState {
        String name;
        transient String cache;
    }

    @Test
    void should_ignore_transient_fields_in_format_instructions_and_schema() {

        // given
        PojoOutputParser<PojoWithTransientState> parser = new PojoOutputParser<>(PojoWithTransientState.class);

        // when
        String formatInstructions = parser.formatInstructions();
        JsonObjectSchema schema =
                (JsonObjectSchema) parser.jsonSchema().orElseThrow().rootElement();

        // then
        assertThat(formatInstructions).contains("\"name\"").doesNotContain("cache");
        assertThat(schema.properties()).containsOnlyKeys("name");
    }

    @Test
    void should_ignore_the_enclosing_instance_of_an_inner_class_in_format_instructions() {

        // given
        class LocalPojo {
            String name;
        }

        PojoOutputParser<LocalPojo> parser = new PojoOutputParser<>(LocalPojo.class);

        // when
        String formatInstructions = parser.formatInstructions();

        // then
        assertThat(formatInstructions).contains("\"name\"").doesNotContain("this$");
    }

    @Test
    void should_create_schema_for_enum_with_custom_toString() {

        // given
        enum MyEnumWithToString {
            A,
            B,
            C;

            @Override
            public String toString() {
                return "[" + name() + "]";
            }
        }

        assertThat(MyEnumWithToString.A.toString()).isEqualTo("[A]");

        class PojoWithEnum {
            private MyEnumWithToString myEnumWithToString;
        }

        PojoOutputParser<PojoWithEnum> parser = new PojoOutputParser<>(PojoWithEnum.class);

        // when
        String formatInstructions = parser.formatInstructions();

        // then
        assertThat(formatInstructions).contains("enum, must be one of [A, B, C]");
    }

    @Test
    void should_create_schema_for_list_of_enums() {

        // given
        enum Status {
            OPEN,
            CLOSED
        }

        class PojoWithEnumList {
            private List<Status> statuses;
        }

        PojoOutputParser<PojoWithEnumList> parser = new PojoOutputParser<>(PojoWithEnumList.class);

        // when
        String formatInstructions = parser.formatInstructions();

        // then
        assertThat(formatInstructions).contains("array of enum, must be one of [OPEN, CLOSED]");
    }
}
