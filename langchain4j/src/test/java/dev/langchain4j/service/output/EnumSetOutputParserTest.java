package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EnumSetOutputParserTest {

    EnumSetOutputParser sut = new EnumSetOutputParser(Weather.class);

    enum Weather {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }

    @Test()
    public void ensureThatOrderIsPreserved() {
        // Given
        String toParse = "SUNNY\nRAINY";

        // When
        Set<Enum> parsed = sut.parse(toParse);

        // Then
        Iterator<Enum> enumIterator = parsed.iterator();
        assertThat(enumIterator.next()).isEqualTo(Weather.SUNNY);
        assertThat(enumIterator.next()).isEqualTo(Weather.RAINY);
    }
}