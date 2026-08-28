package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.model.embedding.onnx.OnnxBertBiEncoder.partition;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class OnnxBertBiEncoderTest {

    @Test
    public void testBasicPartition() {

        // given
        List<String> tokens = asList("[CLS]", "I", "have", "a", "pen", "and", "a", "notebook", "[SEP]");
        int partitionSize = 3;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions)
                .containsExactly(asList("I", "have", "a"), asList("pen", "and", "a"), singletonList("notebook"));
    }

    @Test
    public void testWordSplitAcrossPartitions() {

        // given
        List<String> tokens = asList("[CLS]", "I", "have", "a", "note", "##book", "that", "is", "expensive", "[SEP]");
        int partitionSize = 4;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions)
                .containsExactly(
                        asList("I", "have", "a"),
                        // "note" moved to the next partition to avoid splitting "notebook" across 2 partitions
                        asList("note", "##book", "that", "is"),
                        singletonList("expensive"));
    }

    @Test
    public void testWordSplitAcrossPartitions2() {

        // given
        List<String> tokens =
                asList("[CLS]", "I", "have", "two", "note", "##book", "##s", "that", "are", "expensive", "[SEP]");
        int partitionSize = 5;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions)
                .containsExactly(
                        asList("I", "have", "two"),
                        // "note" and "##book" moved to the next partition to avoid splitting "notebooks" across 2
                        // partitions
                        asList("note", "##book", "##s", "that", "are"),
                        singletonList("expensive"));
    }

    @Test
    public void testPartitionSizeLargerThanList() {

        // given
        List<String> tokens = asList("[CLS]", "I", "have", "[SEP]");
        int partitionSize = 10;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions).containsExactly(asList("I", "have"));
    }

    @Test
    public void testPartitionExactlyFits() {

        // given
        List<String> tokens = asList("[CLS]", "I", "have", "a", "[SEP]");
        int partitionSize = 3;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions).containsExactly(asList("I", "have", "a"));
    }

    @Test
    public void testPartitionWithOnlySpecialTokens() {

        // given - simulates empty or whitespace-only input
        List<String> tokens = asList("[CLS]", "[SEP]");
        int partitionSize = 10;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then - should return empty list (no content tokens between CLS and SEP)
        assertThat(partitions).isEqualTo(emptyList());
    }

    @Test
    @Timeout(5)
    public void testSingleWordSubwordRunLongerThanPartitionSize() {

        // given - a single word whose subword run is longer than partitionSize.
        // Previously the back-off loop that avoids splitting "##" continuations had no lower
        // bound, so "to" descended to "from", producing an empty subList and never advancing
        // "from" -> infinite loop / empty partitions.
        List<String> tokens = asList("[CLS]", "super", "##cali", "##fragi", "##listic", "[SEP]");
        int partitionSize = 1;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then - terminates, no empty partition, every partition has at least one token
        assertThat(partitions).isNotEmpty();
        assertThat(partitions).allSatisfy(partition -> assertThat(partition).isNotEmpty());

        // and - partitions cover all content tokens in order, with no loss or duplication
        List<String> flattened = new ArrayList<>();
        partitions.forEach(flattened::addAll);
        assertThat(flattened).containsExactly("super", "##cali", "##fragi", "##listic");
    }
}
