package dev.langchain4j.service;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;

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
                        "\"birthDate\": (type: date string (2023-12-31))\n" +
                        "}");
    }

    static class PersonWithFirstNameList {
        private List<String> firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    @Test
    void outputFormatInstructions_PersonWithFirstNameList() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithFirstNameList.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: array of string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31))\n" +
                        "}");
    }

    static class PersonWithFirstNameArray {
        private String[] firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    @Test
    void outputFormatInstructions_PersonWithFirstNameArray() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithFirstNameArray.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: array of string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31))\n" +
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
                        "\"birthDate\": (type: java.util.Calendar)\n" +
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
                        "\"birthDate\": (type: date string (2023-12-31))\n" +
                        "}");
    }

    static class Address {
        private Integer streetNumber;
        private String street;
        private String city;
    }

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
                        "\"address\": (type: dev.langchain4j.service.ServiceOutputParserTest$Address: {\n" +
                        "\"streetNumber\": (type: integer),\n" +
                        "\"street\": (type: string),\n" +
                        "\"city\": (type: string)\n" +
                        "})\n" +
                        "}");
    }

    static class PersonAndAddressList {
        private String firstName;
        private String lastName;
        private LocalDate birthDate;
        private List<Address> address;
    }

    @Test
    void outputFormatInstructions_PersonWithNestedObjectList() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonAndAddressList.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "\"address\": (type: array of dev.langchain4j.service.ServiceOutputParserTest$Address: {\n" +
                        "\"streetNumber\": (type: integer),\n" +
                        "\"street\": (type: string),\n" +
                        "\"city\": (type: string)\n" +
                        "})\n" +
                        "}");
    }

    static class PersonAndAddressArray {
        private String firstName;
        private String lastName;
        private LocalDate birthDate;
        private Address[] address;
    }

    @Test
    void outputFormatInstructions_PersonWithNestedObjectArray() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonAndAddressArray.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "\"address\": (type: array of dev.langchain4j.service.ServiceOutputParserTest$Address: {\n" +
                        "\"streetNumber\": (type: integer),\n" +
                        "\"street\": (type: string),\n" +
                        "\"city\": (type: string)\n" +
                        "})\n" +
                        "}");
    }

    static class PersonWithFinalFields {
        private final String firstName;
        private final String lastName;
        private final LocalDate birthDate;

        PersonWithFinalFields(String firstName, String lastName, LocalDate birthDate) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthDate = birthDate;
        }
    }

    @Test
    void outputFormatInstructions_PersonWithFinalFields() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithFinalFields.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31))\n" +
                        "}");
    }

    static class PersonWithParents {
        private String firstName;
        private String lastName;
        private List<PersonWithParents> parents;
    }

    @Test
    void outputFormatInstructions_PersonWithParents() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithParents.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"parents\": (type: array of dev.langchain4j.service.ServiceOutputParserTest$PersonWithParents: {\n" +
                            "\"firstName\": (type: string),\n" +
                            "\"lastName\": (type: string),\n" +
                            "\"parents\": (type: array of dev.langchain4j.service.ServiceOutputParserTest$PersonWithParents)\n" +
                            "})\n" +
                        "}");
    }

    static class PersonWithParentArray {
        private String firstName;
        private String lastName;
        private PersonWithParentArray[] parents;
    }

    @Test
    void outputFormatInstructions_PersonWithParentArray() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithParentArray.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"parents\": (type: array of dev.langchain4j.service.ServiceOutputParserTest$PersonWithParentArray: {\n" +
                            "\"firstName\": (type: string),\n" +
                            "\"lastName\": (type: string),\n" +
                            "\"parents\": (type: array of dev.langchain4j.service.ServiceOutputParserTest$PersonWithParentArray)\n" +
                            "})\n" +
                        "}");
    }

    static class PersonWithMotherAndFather {
        private String firstName;
        private String lastName;
        private PersonWithMotherAndFather mother;
        private PersonWithMotherAndFather father;
    }

    @Test
    void outputFormatInstructions_PersonWithMotherAndFather() {
        String formatInstructions = ServiceOutputParser.outputFormatInstructions(PersonWithMotherAndFather.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"mother\": (type: dev.langchain4j.service.ServiceOutputParserTest$PersonWithMotherAndFather: {\n" +
                            "\"firstName\": (type: string),\n" +
                            "\"lastName\": (type: string),\n" +
                            "\"mother\": (type: dev.langchain4j.service.ServiceOutputParserTest$PersonWithMotherAndFather),\n" +
                            "\"father\": (type: dev.langchain4j.service.ServiceOutputParserTest$PersonWithMotherAndFather)\n" +
                        "}),\n" +
                        "\"father\": (type: dev.langchain4j.service.ServiceOutputParserTest$PersonWithMotherAndFather)\n" +
                        "}");
    }

}