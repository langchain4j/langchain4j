package dev.langchain4j.model.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonOutputParserTest {
    static class Person {
        private String firstName;
        private String lastName;
    }

    private static final JsonOutputParser<Person> SUBJECT = JsonOutputParser.forClass(Person.class);

    @Test
    void parse() {
        final String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\"}";
        final Person person = SUBJECT.parse(json);
        assertEquals("John", person.firstName);
        assertEquals("Doe", person.lastName);
    }

    @Test
    void formatInstructions_prefix_is_ignored() {
        assertEquals("{\n" +
                "\"firstName\": (type: string),\n" +
                "\"lastName\": (type: string)\n" +
                "}", SUBJECT.formatInstructions());
    }
}