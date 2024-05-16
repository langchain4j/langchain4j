package dev.langchain4j.model.output;

import dev.langchain4j.data.message.AiMessage;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CollectionOutputParserTest {
    @Data
    static class Person {
        private String firstName;
        private String lastName;
    }

    interface Fixture {
        List<Person> getPersonList();
    }

    private static final ListOutputParser<Person> PERSON_LIST_PARSER = ListOutputParser.<Person>builder()
            .elementParser(JsonOutputParser.forClass(Person.class))
            .build();

    private static final SetOutputParser<Person> PERSON_SET_PARSER = SetOutputParser.<Person>builder()
            .elementParser(JsonOutputParser.forClass(Person.class))
            .build();

    private static final ListOutputParser<Integer> INTEGER_LIST_PARSER = ListOutputParser.<Integer>builder()
            .elementParser(new IntOutputParser())
            .build();

    private static final SetOutputParser<Integer> INTEGER_SET_PARSER = SetOutputParser.<Integer>builder()
            .elementParser(new IntOutputParser())
            .build();

    private static final ListOutputParser<String> STRING_LIST_PARSER = ListOutputParser.<String>builder()
            .elementParser(new StringOutputParser())
            .build();

    @Test
    public void test_factory() throws Exception {
        final String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\"}\n{\"firstName\":\"Jane\",\"lastName\":\"Doe\"}";
        final ServiceOutputParser parser = ServiceOutputParser.createDefault(Fixture.class.getMethod("getPersonList"));
        final Object parsed = parser.parse(OutputParsingContext.builder()
                .response(Response.from(AiMessage.from(json)))
                .build());
        System.out.println(parsed);
    }

    @Test
    void parse_person_list() {
        final String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\"}\n{\"firstName\":\"Jane\",\"lastName\":\"Doe\"}";
        final List<Person> people = PERSON_LIST_PARSER.parse(json);
        assertEquals(2, people.size());
        assertEquals("John", people.get(0).firstName);
        assertEquals("Doe", people.get(0).lastName);
        assertEquals("Jane", people.get(1).firstName);
        assertEquals("Doe", people.get(1).lastName);
    }

    @Test
    void parse_person_set() {
        final String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\"}\n{\"firstName\":\"Jane\",\"lastName\":\"Doe\"}";
        final Set<Person> people = PERSON_SET_PARSER.parse(json);
        assertEquals(2, people.size());
    }

    @Test
    void formatInstructions_person_list() {
        assertEquals("{\n\"firstName\": (type: string),\n" +
                "\"lastName\": (type: string)\n" +
                "}", PERSON_LIST_PARSER.formatInstructions());
        assertEquals("\nYou must put every item on a separate line.", PERSON_LIST_PARSER.customFormatPostlude());
    }

    @Test
    void parse_integer_list() {
        final String json = "1\n2\n3";
        final List<Integer> numbers = INTEGER_LIST_PARSER.parse(json);
        assertEquals(3, numbers.size());
        assertEquals(1, numbers.get(0));
        assertEquals(2, numbers.get(1));
        assertEquals(3, numbers.get(2));
    }

    @Test
    void parse_integer_set() {
        final String json = "1\n2\n3";
        final Set<Integer> numbers = INTEGER_SET_PARSER.parse(json);
        assertEquals(3, numbers.size());
    }

    @Test
    void formatInstructions_integer_list() {
        assertEquals("integer number", INTEGER_LIST_PARSER.formatInstructions());
        assertEquals("\nYou must put every item on a separate line.", INTEGER_LIST_PARSER.customFormatPostlude());
    }

    @Test
    void parse_string_list() {
        final String json = "hello\nworld";
        final List<String> strings = STRING_LIST_PARSER.parse(json);
        assertEquals(2, strings.size());
        assertEquals("hello", strings.get(0));
        assertEquals("world", strings.get(1));
    }

    @Test
    void formatInstructions_string_list() {
        assertNull(STRING_LIST_PARSER.formatInstructions());
        assertEquals("\nYou must put every item on a separate line.", STRING_LIST_PARSER.customFormatPostlude());
    }
}