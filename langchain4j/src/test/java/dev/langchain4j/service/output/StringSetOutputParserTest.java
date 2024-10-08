package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(setIterator.next(), "one");
        assertEquals(setIterator.next(), "two");
        assertEquals(setIterator.next(), "three");
        assertEquals(setIterator.next(), "four");
        assertEquals(setIterator.next(), "five");
        assertEquals(setIterator.next(), "six");
        assertEquals(setIterator.next(), "seven");
        assertEquals(setIterator.next(), "eight");
        assertEquals(setIterator.next(), "nine");
        assertEquals(setIterator.next(), "ten");
    }
}