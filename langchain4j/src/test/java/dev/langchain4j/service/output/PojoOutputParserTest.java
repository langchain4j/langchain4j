package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.junit.jupiter.api.Test;

class PojoOutputParserTest {

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

    @Test
    void should_include_inherited_fields_in_format_instructions() {

        // given
        PojoOutputParser<ChildDto> parser = new PojoOutputParser<>(ChildDto.class);

        // when
        String formatInstructions = parser.formatInstructions();

        // then
        assertThat(formatInstructions)
                .contains("Request status")
                .contains("\"status\"")
                .contains("\"message\"")
                .contains("The answer")
                .contains("\"answer\"");
        assertThat(formatInstructions.indexOf("\"status\"")).isLessThan(formatInstructions.indexOf("\"answer\""));
    }

    @Test
    void should_not_fail_for_pojo_with_only_inherited_fields() {

        // given - before the fix this threw IllegalConfigurationException: Illegal method return type
        PojoOutputParser<OnlyInheritedFieldsDto> parser = new PojoOutputParser<>(OnlyInheritedFieldsDto.class);

        // when
        String formatInstructions = parser.formatInstructions();

        // then
        assertThat(formatInstructions).contains("\"status\"").contains("\"message\"");
    }

    @Test
    void should_parse_json_with_inherited_fields() {

        // given
        PojoOutputParser<ChildDto> parser = new PojoOutputParser<>(ChildDto.class);

        // when
        ChildDto dto = parser.parse("{\"status\":\"SUCCESS\",\"message\":\"all good\",\"answer\":\"42\"}");

        // then
        assertThat(dto.getStatus()).isEqualTo("SUCCESS");
        assertThat(dto.getMessage()).isEqualTo("all good");
        assertThat(dto.getAnswer()).isEqualTo("42");
    }

    @Test
    void should_not_duplicate_shadowed_fields() {

        // given
        PojoOutputParser<ShadowingChildDto> parser = new PojoOutputParser<>(ShadowingChildDto.class);

        // when
        String formatInstructions = parser.formatInstructions();

        // then - the subclass field shadows the inherited one and appears exactly once
        assertThat(countOccurrences(formatInstructions, "\"value\"")).isEqualTo(1);
        assertThat(formatInstructions).contains("Overridden description").contains("\"extra\"");
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    static class BaseDto {

        @Description("Request status - SUCCESS OR ERROR")
        private String status;

        @Description("Descriptive message in case of error, success or other information")
        private String message;

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    static class ChildDto extends BaseDto {

        @Description("The answer to the question")
        private String answer;

        public String getAnswer() {
            return answer;
        }
    }

    static class OnlyInheritedFieldsDto extends BaseDto {}

    static class ShadowingBaseDto {

        @Description("Base description")
        private String value;
    }

    static class ShadowingChildDto extends ShadowingBaseDto {

        @Description("Overridden description")
        private String value;

        private String extra;
    }
}
