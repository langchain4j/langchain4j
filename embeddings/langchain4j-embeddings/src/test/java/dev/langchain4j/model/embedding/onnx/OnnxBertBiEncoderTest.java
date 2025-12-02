package dev.langchain4j.model.embedding.onnx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.embedding.onnx.OnnxBertBiEncoder.partition;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class OnnxBertBiEncoderTest {

    @Test
    public void testBasicPartition() {

        // given
        List<String> tokens = asList("[CLS]", "I", "have", "a", "pen", "and", "a", "notebook", "[SEP]");
        int partitionSize = 3;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions).containsExactly(
                asList("I", "have", "a"),
                asList("pen", "and", "a"),
                singletonList("notebook")
        );
    }

    @Test
    public void testWordSplitAcrossPartitions() {

        // given
        List<String> tokens = asList("[CLS]", "I", "have", "a", "note", "##book", "that", "is", "expensive", "[SEP]");
        int partitionSize = 4;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions).containsExactly(
                asList("I", "have", "a"),
                // "note" moved to the next partition to avoid splitting "notebook" across 2 partitions
                asList("note", "##book", "that", "is"),
                singletonList("expensive")
        );
    }

    @Test
    public void testWordSplitAcrossPartitions2() {

        // given
        List<String> tokens = asList("[CLS]", "I", "have", "two", "note", "##book", "##s", "that", "are", "expensive", "[SEP]");
        int partitionSize = 5;

        // when
        List<List<String>> partitions = partition(tokens, partitionSize);

        // then
        assertThat(partitions).containsExactly(
                asList("I", "have", "two"),
                // "note" and "##book" moved to the next partition to avoid splitting "notebooks" across 2 partitions
                asList("note", "##book", "##s", "that", "are"),
                singletonList("expensive")
        );
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
}