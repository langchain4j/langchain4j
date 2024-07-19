package dev.langchain4j.service.output;

import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class ServiceOutputParserTest {

    ServiceOutputParser sut = new ServiceOutputParser();

    @Test
    void makeSureThatCorrectOutputParserIsUsedForParsing() {
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("true"), boolean.class, BooleanOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("true"), Boolean.class, BooleanOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), byte.class, ByteOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), Byte.class, ByteOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), short.class, ShortOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), Short.class, ShortOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), int.class, IntOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), Integer.class, IntOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), long.class, LongOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), Long.class, LongOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), BigInteger.class, BigIntegerOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), float.class, FloatOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), Float.class, FloatOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), double.class, DoubleOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), Double.class, DoubleOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("1"), BigDecimal.class, BigDecimalOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("2024-07-02"), Date.class, DateOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("2024-07-02"), LocalDate.class, LocalDateOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("11:38:00"), LocalTime.class, LocalTimeOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("2024-07-02T11:38:00"), LocalDateTime.class, LocalDateTimeOutputParser.class);
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage(Weather.SUNNY.name()), Weather.class, EnumOutputParser.class);
        Type listOfWeatherEnumTypes = new TypeToken<List<Weather>>() {
        }.getType();
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("SUNNY\nCLOUDY"), listOfWeatherEnumTypes, EnumListOutputParser.class);

        Type setOfWeatherEnumTypes = new TypeToken<Set<Weather>>() {
        }.getType();
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("SUNNY\nCLOUDY"), setOfWeatherEnumTypes, EnumSetOutputParser.class);

        Type listOfStringsType = new TypeToken<List<String>>() {
        }.getType();
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("SUNNY\nCLOUDY"), listOfStringsType, StringListOutputParser.class);

        Type setOfStringsType = new TypeToken<Set<String>>() {
        }.getType();
        testWhetherProperOutputParserWasCalled(AiMessage.aiMessage("SUNNY\nCLOUDY"), setOfStringsType, StringSetOutputParser.class);
    }

    private void testWhetherProperOutputParserWasCalled(AiMessage aiMessage, Type rawReturnType, Class<?> expectedOutputParserType) {
        // Given
        DefaultOutputParserFactory defaultOutputParserFactory = new DefaultOutputParserFactory();
        OutputParserFactory defaultOutputParserFactorySpy = spy(defaultOutputParserFactory);

        Response<AiMessage> responseStub = Response.from(aiMessage);
        sut = new ServiceOutputParser(defaultOutputParserFactorySpy);

        AtomicReference<Optional<OutputParser<?>>> capturedParserReference = new AtomicReference<>();

        doAnswer((Answer<Optional<?>>) invocation -> {
            Optional<OutputParser<?>> result = (Optional<OutputParser<?>>) invocation.callRealMethod();
            capturedParserReference.set(result);
            return result;
        }).when(defaultOutputParserFactorySpy).get(any(), any());

        // When
        sut.parse(responseStub, rawReturnType);

        // Then
        Object capturedOutputParser = capturedParserReference.get().get();
        assertInstanceOf(expectedOutputParserType, capturedOutputParser);
    }

    /********************************************************************************************
     * Output format instructions tests
     ********************************************************************************************/

    public enum Weather {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }

    public enum WeatherWithDescription {
        @Description("A clear day with bright sunlight and few or no clouds")
        SUNNY,
        @Description("The sky is covered with clouds, often creating a gray and overcast appearance")
        CLOUDY,
        @Description("Precipitation in the form of rain, with cloudy skies and wet conditions")
        RAINY,
        @Description("Snowfall occurs, covering the ground in white and creating cold, wintry conditions")
        SNOWY
    }

    static class Person {
        private String firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    @Test
    void outputFormatInstructions_Enum() {
        String formatInstructions = sut.outputFormatInstructions(Weather.class);

        assertThat(formatInstructions).isEqualTo(
                "\n" +
                        "You must answer strictly with one of these enums:\n" +
                        "SUNNY\n" +
                        "CLOUDY\n" +
                        "RAINY\n" +
                        "SNOWY");
    }

    @Test
    void outputFormatInstructions_EnumWithDescriptions() {
        String formatInstructions = sut.outputFormatInstructions(WeatherWithDescription.class);

        assertThat(formatInstructions).isEqualTo(
                "\n" +
                        "You must answer strictly with one of these enums:\n" +
                        "SUNNY - A clear day with bright sunlight and few or no clouds\n" +
                        "CLOUDY - The sky is covered with clouds, often creating a gray and overcast appearance\n" +
                        "RAINY - Precipitation in the form of rain, with cloudy skies and wet conditions\n" +
                        "SNOWY - Snowfall occurs, covering the ground in white and creating cold, wintry conditions");
    }

    @Test
    void outputFormatInstructions_SimplePerson() {

        String formatInstructions = sut.outputFormatInstructions(Person.class);

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
        String formatInstructions = sut.outputFormatInstructions(PersonWithFirstNameList.class);

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
        String formatInstructions = sut.outputFormatInstructions(PersonWithFirstNameArray.class);

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
        String formatInstructions = sut.outputFormatInstructions(PersonWithCalendarDate.class);

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
        String formatInstructions = sut.outputFormatInstructions(PersonWithStaticField.class);

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
        String formatInstructions = sut.outputFormatInstructions(PersonAndAddress.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "\"address\": (type: dev.langchain4j.service.output.ServiceOutputParserTest$Address: {\n" +
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
        String formatInstructions = sut.outputFormatInstructions(PersonAndAddressList.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "\"address\": (type: array of dev.langchain4j.service.output.ServiceOutputParserTest$Address: {\n" +
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
        String formatInstructions = sut.outputFormatInstructions(PersonAndAddressArray.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "\"address\": (type: array of dev.langchain4j.service.output.ServiceOutputParserTest$Address: {\n" +
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
        String formatInstructions = sut.outputFormatInstructions(PersonWithFinalFields.class);

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
        String formatInstructions = sut.outputFormatInstructions(PersonWithParents.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"parents\": (type: array of dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithParents: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"parents\": (type: array of dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithParents)\n" +
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
        String formatInstructions = sut.outputFormatInstructions(PersonWithParentArray.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"parents\": (type: array of dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithParentArray: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"parents\": (type: array of dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithParentArray)\n" +
                        "})\n" +
                        "}");
    }

    @Test
    void outputFormatInstructions_ClassWithNoFields() {
        String formatInstructions = sut.outputFormatInstructions(ClassWithNoFields.class);

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
        String formatInstructions = sut.outputFormatInstructions(PersonWithMotherAndFather.class);

        assertThat(formatInstructions).isEqualTo(
                "\nYou must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"mother\": (type: dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithMotherAndFather: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"mother\": (type: dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithMotherAndFather),\n" +
                        "\"father\": (type: dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithMotherAndFather)\n" +
                        "}),\n" +
                        "\"father\": (type: dev.langchain4j.service.output.ServiceOutputParserTest$PersonWithMotherAndFather)\n" +
                        "}");
    }
}