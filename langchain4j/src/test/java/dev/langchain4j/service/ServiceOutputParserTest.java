package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.*;
import dev.langchain4j.model.output.structured.Parse;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceOutputParserTest {

    static interface Fixture {
        Person getPerson();

        @Parse(parser = CustomPersonParser.class)
        Person getPersonCustomParser();

        @Parse(factory = CustomPersonParserFactory.class)
        Person getPersonCustomFactory();

        PersonWithFirstNameArray getPersonWithFirstNameArray();

        PersonWithFirstNameList getPersonWithFirstNameList();

        PersonAndAddress getPersonAndAddress();

        PersonAndAddressList getPersonAndAddressList();

        PersonAndAddressArray getPersonAndAddressArray();

        PersonWithCalendarDate getPersonWithCalendarDate();

        PersonWithStaticField getPersonWithStaticField();

        PersonWithFinalFields getPersonWithFinalFields();

        PersonWithParents getPersonWithParents();

        PersonWithParentArray getPersonWithParentArray();

        PersonWithMotherAndFather getPersonWithMotherAndFather();

        ClassWithNoFields getClassWithNoFields();

        int getInt();

        List<Integer> getIntegerList();

        List<Person> getPersonList();

        String getString();

        AiMessage getAiMessage();

        Response<AiMessage> getResponse();

        @SneakyThrows
        static ServiceOutputParser method(final String methodName) {
            return ServiceOutputParser.createDefault(Fixture.class.getMethod(methodName));
        }
    }

    static OutputParsingContext asContext(final String text) {
        return OutputParsingContext.builder()
                .response(Response.from(AiMessage.from(text)))
                .sources(null)
                .tokenUsage(null)
                .build();
    }

    @Test
    void outputFormatInstructions_Int() {
        String formatInstructions = Fixture.method("getInt").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("\nYou must answer strictly in the following format: integer number");
    }

    @Test
    void outputFormatInstructions_String() {
        String formatInstructions = Fixture.method("getString").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("");
    }

    @Test
    void outputFormatInstructions_AiMessage() {
        String formatInstructions = Fixture.method("getAiMessage").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("");
    }

    @Test
    void outputFormatInstructions_Response() {
        String formatInstructions = Fixture.method("getResponse").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("");
    }

    @Test
    void outputFormatInstructions_IntegerList() {
        String formatInstructions = Fixture.method("getIntegerList").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("\n" +
                "You must answer strictly in the following format: integer number\n" +
                "You must put every item on a separate line.");
    }

    @Test
    void outputFormatInstructions_PersonList() {
        String formatInstructions = Fixture.method("getPersonList").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("\n" +
                "You must answer strictly in the following JSON format: {\n" +
                "\"firstName\": (type: string),\n" +
                "\"lastName\": (type: string),\n" +
                "\"birthDate\": (type: date string (2023-12-31))\n" +
                "}\n" +
                "You must put every item on a separate line.");
    }

    static class Person {
        private String firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    @Test
    void outputFormatInstructions_SimplePerson() {
        String formatInstructions = Fixture.method("getPerson").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31))\n" +
                        "}");
    }

    @Test
    void parseOutput_SimplePerson() {
        String text = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"birthDate\":\"2023-12-31\"}";
        Person person = (Person) Fixture.method("getPerson").parse(asContext(text));

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(2023, 12, 31));
    }


    @Test
    void outputFormatInstructions_custom_parser() {
        String formatInstructions = Fixture.method("getPersonCustomParser").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("\nYou must answer strictly in the following format: CSV format: firstName,lastName,birthDate");
    }

    @Test
    void parseOutput_custom_parser() {
        String text = "John,Doe,2023-12-31";
        Person person = (Person) Fixture.method("getPersonCustomParser").parse(asContext(text));

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    @Test
    void outputFormatInstructions_custom_factory() {
        String formatInstructions = Fixture.method("getPersonCustomFactory").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("\nYou must answer strictly in the following format: CSV format: firstName,lastName,birthDate");
    }

    @Test
    void parseOutput_custom_factory() {
        String text = "John,Doe,2023-12-31";
        Person person = (Person) Fixture.method("getPersonCustomFactory").parse(asContext(text));

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    static class PersonWithFirstNameList {
        private List<String> firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    @Test
    void outputFormatInstructions_PersonWithFirstNameList() {
        String formatInstructions = Fixture.method("getPersonWithFirstNameList").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonWithFirstNameArray").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonWithCalendarDate").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonWithStaticField").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonAndAddress").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonAndAddressList").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonAndAddressArray").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonWithFinalFields").outputFormatInstructions();

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
        String formatInstructions = Fixture.method("getPersonWithParents").outputFormatInstructions();

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

    static class ClassWithNoFields {

    }

    @Test
    void outputFormatInstructions_PersonWithParentArray() {
        String formatInstructions = Fixture.method("getPersonWithParentArray").outputFormatInstructions();

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

    @Test
    void outputFormatInstructions_ClassWithNoFields() {
        String formatInstructions = Fixture.method("getClassWithNoFields").outputFormatInstructions();

        assertThat(formatInstructions).isEqualTo("\n" +
                "You must answer strictly in the following JSON format: {\n" +
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
        String formatInstructions = Fixture.method("getPersonWithMotherAndFather").outputFormatInstructions();

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

    public static class CustomPersonParser implements TextOutputParser<Person> {

        @Override
        public Set<Class<?>> getSupportedTypes() {
            return setOf(Person.class);
        }

        @Override
        public Person parse(final String text) {
            final String[] split = text.split(",");
            final Person person = new Person();
            person.firstName = split[0];
            person.lastName = split[1];
            person.birthDate = LocalDate.parse(split[2]);
            return person;
        }

        @Override
        public String formatInstructions() {
            return "CSV format: firstName,lastName,birthDate";
        }
    }

    public static class CustomPersonParserFactory implements ParserFactory {
        @Override
        public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
            return Optional.of(new CustomPersonParser());
        }
    }
}