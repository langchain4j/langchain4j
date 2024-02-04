package dev.langchain4j.service;

import lombok.ToString;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Calendar;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceOutputParserTest {

    static class Person {
        private String firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    @Test
    void outputFormatInstructions_SimplePerson() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(Person.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "}");
    }

    static class PersonWithCalendarDate {
        private String firstName;
        private String lastName;
        private Calendar birthDate;
    }

    @Test
    void outputFormatInstructions_PersonWithJavaType() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithCalendarDate.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: java.util.Calendar),\n" +
                        "}");
    }

    static class PersonWithStaticField implements Serializable {
        private static final long serialVersionUID = 1234567L;
        private String firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    @Test
    void outputFormatInstructions_PersonWithStaticFinalField() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithStaticField.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "}");
    }


    @ToString
    static class Address  {
        private Integer streetNumber;
        private String street;
        private String city;
    }
    @ToString
    static class PersonAndAddress {
        private String firstName;
        private String lastName;
        private LocalDate birthDate;
        private Address address;
    }
    @Test
    void outputFormatInstructions_PersonWithNestedObject() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonAndAddress.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "\"address\": (type: {\n" +
                        "\"streetNumber\": (type: integer),\n" +
                        "\"street\": (type: string),\n" +
                        "\"city\": (type: string),\n" +
                        "}),\n" +
                        "}");
    }

}