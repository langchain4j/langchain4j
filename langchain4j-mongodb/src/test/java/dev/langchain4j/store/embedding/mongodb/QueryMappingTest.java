package dev.langchain4j.store.embedding.mongodb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryMappingTest {
    QueryMapping queryMapping = new QueryMapping();

    @Test
    void asDoublesList() {
        List<Double> doublesList = queryMapping.asDoublesList(new float[]{1f, 2f, 1 / 3f});

        assertThat(doublesList).hasSize(3);
        assertThat(doublesList.get(0)).isEqualTo(1D);
        assertThat(doublesList.get(1)).isEqualTo(2D);
        assertThat(doublesList.get(2)).isEqualTo(1 / 3D, within(0.00000001D));

    }

    @Test
    void asDoublesListRequiredInput() {
        assertThrows(NullPointerException.class, () -> {
            queryMapping.asDoublesList(null);
        });
    }
}