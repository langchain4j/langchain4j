package dev.langchain4j.retriever;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class RetrieverTest implements WithAssertions {
    public static class TestRetriever implements Retriever<String> {
        @Override
        public List<String> findRelevant(String text) {
            return Collections.singletonList("abc");
        }
    }

    @Test
    public void testFindRelevant() {
        Retriever<String> retriever = new TestRetriever();
        assertThat(retriever.findRelevant("test"))
                .containsOnly("abc");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> retriever.findRelevant("test", "test"))
                .withMessage("Not implemented");
    }
}