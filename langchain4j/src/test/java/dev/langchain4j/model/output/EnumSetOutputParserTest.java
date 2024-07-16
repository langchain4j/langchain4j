package dev.langchain4j.model.output;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumSetOutputParserTest {

    enum Weather {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }


    private final EnumSetOutputParser sut = new EnumSetOutputParser(Weather.class);

    @Test()
    public void ensureThatOrderIsPreserved() {
        // Given
        String toParse = "SUNNY\nRAINY";

        // When
        Set<Enum> parsed = sut.parse(toParse);

        // Then
        Iterator<Enum> enumIterator = parsed.iterator();
        assertEquals(Weather.SUNNY, enumIterator.next());
        assertEquals(Weather.RAINY, enumIterator.next());

    }
}