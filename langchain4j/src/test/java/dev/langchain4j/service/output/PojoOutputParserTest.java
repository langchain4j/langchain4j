package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.junit.jupiter.api.Test;

class PojoOutputParserTest {

    abstract static class Parent {
        @Description("status")
        String status;

        String message;
    }

    static class Child extends Parent {
        String payload;
    }

    static class GrandChild extends Child {
        // shadow parent field; subclass value should win (and no duplicate entry)
        String status;
    }

    @Test
    void should_include_inherited_fields_in_format_instructions() {

        PojoOutputParser<Child> parser = new PojoOutputParser<>(Child.class);

        String formatInstructions = parser.formatInstructions();

        assertThat(formatInstructions)
                .contains("\"payload\":")
                .contains("\"status\":")
                .contains("\"message\":");
    }

    @Test
    void should_not_duplicate_shadowed_fields() {

        PojoOutputParser<GrandChild> parser = new PojoOutputParser<>(GrandChild.class);

        String formatInstructions = parser.formatInstructions();

        int firstStatus = formatInstructions.indexOf("\"status\":");
        int lastStatus = formatInstructions.lastIndexOf("\"status\":");
        assertThat(firstStatus).isGreaterThanOrEqualTo(0);
        assertThat(firstStatus).isEqualTo(lastStatus);
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
