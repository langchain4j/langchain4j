package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PojoOutputParserTest {

    @Test
    void should_create_schema_for_enum_with_custom_toString() {

        // given
        enum MyEnumWithToString {
            A, B, C;

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
}