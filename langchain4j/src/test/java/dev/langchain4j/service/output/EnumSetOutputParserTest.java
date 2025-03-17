package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnumSetOutputParserTest {

    EnumSetOutputParser sut = new EnumSetOutputParser(Weather.class);

    enum Weather {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }

    @Test()
    void ensureThatOrderIsPreserved() {
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
