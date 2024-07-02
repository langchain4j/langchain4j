package dev.langchain4j.model.output;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EnumOutputParserTest {

    private final EnumOutputParser sut = new EnumOutputParser(WEATHER.class);

    enum WEATHER {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }

    @Test
    public void generateInstruction() {
        // When
        String instruction = sut.formatInstructions();

        // Then
        assertEquals("one of [SUNNY, CLOUDY, RAINY, SNOWY]", instruction);
    }

    @Test
    public void parseResponse() {
        // When
        Enum<?> resultedEnum = sut.parse(WEATHER.SUNNY.name());

        // Then
        assertEquals(WEATHER.SUNNY, resultedEnum);
    }

    @Test
    public void parseResponseWithSpaces() {
        // When
        Enum<?> resultedEnum = sut.parse(" " + WEATHER.SUNNY.name() + "    ");

        // Then
        assertEquals(WEATHER.SUNNY, resultedEnum);
    }

    @Test
    public void parseResponseWithBrackets() {
        // When
        Enum<?> resultedEnum = sut.parse(" [  " + WEATHER.SUNNY.name() + "  ]  ");

        // Then
        assertEquals(WEATHER.SUNNY, resultedEnum);
    }


}