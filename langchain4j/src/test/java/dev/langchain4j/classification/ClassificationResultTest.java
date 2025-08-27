package dev.langchain4j.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassificationResultTest {

    @Test
    void equals_shouldReturnFalse_whenComparedWithNull() {
        ClassificationResult<String> result = new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9)));
        assertNotEquals(null, result);
    }

    @Test
    void equals_shouldReturnFalse_whenComparedWithDifferentClass() {
        ClassificationResult<String> result = new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9)));
        assertNotEquals("some string", result);
    }

    @Test
    void equals_shouldReturnTrue_whenComparedWithItself() {
        ClassificationResult<String> result = new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9)));
        assertEquals(result, result);
    }

    @Test
    void equals_shouldReturnTrue_whenScoredLabelsAreEqual() {
        ClassificationResult<String> result1 =
                new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9), new ScoredLabel<>("Label2", 0.8)));
        ClassificationResult<String> result2 =
                new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9), new ScoredLabel<>("Label2", 0.8)));
        assertEquals(result1, result2);
        assertEquals(result2, result1);
    }

    @Test
    void equals_shouldReturnFalse_whenScoredLabelsDifferInSize() {
        ClassificationResult<String> result1 = new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9)));
        ClassificationResult<String> result2 =
                new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9), new ScoredLabel<>("Label2", 0.8)));
        assertNotEquals(result1, result2);
    }

    @Test
    void equals_shouldReturnFalse_whenScoredLabelsHaveDifferentValues() {
        ClassificationResult<String> result1 = new ClassificationResult<>(List.of(new ScoredLabel<>("Label1", 0.9)));
        ClassificationResult<String> result2 = new ClassificationResult<>(
                List.of(new ScoredLabel<>("Label1", 0.8)) // different score
                );
        assertNotEquals(result1, result2);
    }

    @Test
    void equals_shouldReturnTrue_whenListInstancesAreDifferentButContentIsSame() {
        List<ScoredLabel<String>> list1 =
                Arrays.asList(new ScoredLabel<>("Label1", 0.9), new ScoredLabel<>("Label2", 0.8));

        List<ScoredLabel<String>> list2 =
                Arrays.asList(new ScoredLabel<>("Label1", 0.9), new ScoredLabel<>("Label2", 0.8));

        ClassificationResult<String> result1 = new ClassificationResult<>(list1);
        ClassificationResult<String> result2 = new ClassificationResult<>(list2);

        assertEquals(result1, result2);
    }

    @Test
    void equals_shouldReturnTrue_whenEmptyListsAreCompared() {
        ClassificationResult<String> result1 = new ClassificationResult<>(Collections.emptyList());
        ClassificationResult<String> result2 = new ClassificationResult<>(Collections.emptyList());

        assertEquals(result1, result2);
    }
}
