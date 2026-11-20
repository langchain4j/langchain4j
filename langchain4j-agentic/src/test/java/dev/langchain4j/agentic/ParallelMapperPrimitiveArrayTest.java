package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.service.V;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A parallel mapper accepts an array of items and can return an array of results, which may be arrays of a primitive
 * type, like {@code int[]}, rather than of objects.
 */
class ParallelMapperPrimitiveArrayTest {

    public static class Doubler {

        @Agent(description = "Doubles a number", outputKey = "doubled")
        public int doubleIt(@V("number") int number) {
            return number * 2;
        }
    }

    public interface IntArrayToListMapper {

        @Agent
        List<Integer> mapAll(@V("numbers") int[] numbers);
    }

    public interface IntArrayToIntArrayMapper {

        @Agent
        int[] mapAll(@V("numbers") int... numbers);
    }

    public interface DoubleArrayToListMapper {

        @Agent
        List<Integer> mapAll(@V("numbers") double[] numbers);
    }

    @Test
    void items_of_a_primitive_array_are_mapped_one_by_one() {
        IntArrayToListMapper mapper = AgenticServices.parallelMapperBuilder(IntArrayToListMapper.class)
                .subAgents(new Doubler())
                .build();

        assertThat(mapper.mapAll(new int[] {1, 2, 3})).containsExactly(2, 4, 6);
    }

    @Test
    void items_of_an_empty_primitive_array_are_mapped_to_an_empty_list() {
        IntArrayToListMapper mapper = AgenticServices.parallelMapperBuilder(IntArrayToListMapper.class)
                .subAgents(new Doubler())
                .build();

        assertThat(mapper.mapAll(new int[0])).isEmpty();
    }

    @Test
    void items_of_a_primitive_array_are_converted_to_the_type_of_the_subagent_argument() {
        DoubleArrayToListMapper mapper = AgenticServices.parallelMapperBuilder(DoubleArrayToListMapper.class)
                .subAgents(new Doubler())
                .build();

        assertThat(mapper.mapAll(new double[] {1.0, 2.0})).containsExactly(2, 4);
    }

    @Test
    void results_are_returned_as_a_primitive_array() {
        IntArrayToIntArrayMapper mapper = AgenticServices.parallelMapperBuilder(IntArrayToIntArrayMapper.class)
                .subAgents(new Doubler())
                .build();

        assertThat(mapper.mapAll(1, 2, 3)).containsExactly(2, 4, 6);
    }

    @Test
    void an_empty_primitive_array_is_mapped_to_an_empty_primitive_array() {
        IntArrayToIntArrayMapper mapper = AgenticServices.parallelMapperBuilder(IntArrayToIntArrayMapper.class)
                .subAgents(new Doubler())
                .build();

        assertThat(mapper.mapAll()).isEmpty();
    }
}
