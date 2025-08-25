package dev.langchain4j.classification;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScoredLabelTest {

    @Test
    void equals_shouldReturnFalse_whenComparedWithNull() {
        ScoredLabel<String> label = new ScoredLabel<>("Label1", 0.9);
        assertNotEquals(null, label);
    }

    @Test
    void equals_shouldReturnFalse_whenComparedWithDifferentClass() {
        ScoredLabel<String> label = new ScoredLabel<>("Label1", 0.9);
        assertNotEquals("some string", label);
    }

    @Test
    void equals_shouldReturnTrue_whenComparedWithItself() {
        ScoredLabel<String> label = new ScoredLabel<>("Label1", 0.9);
        assertEquals(label, label);
    }

    @Test
    void equals_shouldReturnTrue_whenLabelAndScoreAreEqual() {
        ScoredLabel<String> label1 = new ScoredLabel<>("Label1", 0.9);
        ScoredLabel<String> label2 = new ScoredLabel<>("Label1", 0.9);
        assertEquals(label1, label2);
        assertEquals(label2, label1);
    }

    @Test
    void equals_shouldReturnFalse_whenLabelsAreDifferent() {
        ScoredLabel<String> label1 = new ScoredLabel<>("Label1", 0.9);
        ScoredLabel<String> label2 = new ScoredLabel<>("Label2", 0.9);
        assertNotEquals(label1, label2);
    }

    @Test
    void equals_shouldReturnFalse_whenScoresAreDifferent() {
        ScoredLabel<String> label1 = new ScoredLabel<>("Label1", 0.9);
        ScoredLabel<String> label2 = new ScoredLabel<>("Label1", 0.8);
        assertNotEquals(label1, label2);
    }

    @Test
    void equals_shouldHandleDoubleNaNCorrectly() {
        ScoredLabel<String> label1 = new ScoredLabel<>("Label1", Double.NaN);
        ScoredLabel<String> label2 = new ScoredLabel<>("Label1", Double.NaN);
        assertEquals(label1, label2); // because you use Double.doubleToLongBits
    }

    @Test
    void equals_shouldDifferentiateNegativeZeroAndZero() {
        ScoredLabel<String> label1 = new ScoredLabel<>("Label1", 0.0);
        ScoredLabel<String> label2 = new ScoredLabel<>("Label1", -0.0);
        assertFalse(label1.equals(label2)); // because 0.0 != -0.0 in long bits
    }
}
