package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StringSetOutputParserTest {

    StringSetOutputParser sut = new StringSetOutputParser();

    @Test()
    public void ensureThatOrderIsPreserved() {
        // Given
        String toParse = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten";

        // When
        Set<String> parsedSet = sut.parse(toParse);

        // Then
        Iterator<String> setIterator = parsedSet.iterator();
        assertThat("one").isEqualTo(setIterator.next());
        assertThat("two").isEqualTo(setIterator.next());
        assertThat("three").isEqualTo(setIterator.next());
        assertThat("four").isEqualTo(setIterator.next());
        assertThat("five").isEqualTo(setIterator.next());
        assertThat("six").isEqualTo(setIterator.next());
        assertThat("seven").isEqualTo(setIterator.next());
        assertThat("eight").isEqualTo(setIterator.next());
        assertThat("nine").isEqualTo(setIterator.next());
        assertThat("ten").isEqualTo(setIterator.next());
    }
}