package dev.langchain4j.store.embedding.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.document.Metadata;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the normalization of metadata values read back from the database. A JSON mapping provider hands over
 * types that {@link Metadata} does not accept, such as {@link BigInteger} or {@link Short}, so they are converted to the
 * closest supported type. These tests run without a live database: the store instance is created via Mockito (bypassing
 * the regular constructor) and {@code createMetadata} is invoked through reflection so the real method body executes.
 */
class HibernateEmbeddingStoreMetadataTest {

    private static Metadata createMetadata(Map<?, ?> map) throws Exception {
        @SuppressWarnings("unchecked")
        final HibernateEmbeddingStore<?> store = mock(HibernateEmbeddingStore.class, CALLS_REAL_METHODS);
        final Method method = HibernateEmbeddingStore.class.getDeclaredMethod("createMetadata", Map.class);
        method.setAccessible(true);
        try {
            return (Metadata) method.invoke(store, map);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void should_keep_the_types_that_metadata_supports() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("string", "a value");
        values.put("integer", 1);
        values.put("long", 2L);
        values.put("float", 3.5f);
        values.put("double", 4.5d);
        values.put("uuid", uuid);

        final Metadata metadata = createMetadata(values);

        assertThat(metadata.getString("string")).isEqualTo("a value");
        assertThat(metadata.getInteger("integer")).isEqualTo(1);
        assertThat(metadata.getLong("long")).isEqualTo(2L);
        assertThat(metadata.getFloat("float")).isEqualTo(3.5f);
        assertThat(metadata.getDouble("double")).isEqualTo(4.5d);
        assertThat(metadata.getUUID("uuid")).isEqualTo(uuid);
    }

    @Test
    void should_narrow_the_number_types_that_metadata_does_not_support() throws Exception {
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("byte", (byte) 1);
        values.put("short", (short) 2);
        values.put("bigInteger", BigInteger.valueOf(3L));
        values.put("bigDecimal", new BigDecimal("4.5"));

        final Metadata metadata = createMetadata(values);

        assertThat(metadata.getInteger("byte")).isEqualTo(1);
        assertThat(metadata.getInteger("short")).isEqualTo(2);
        assertThat(metadata.getLong("bigInteger")).isEqualTo(3L);
        assertThat(metadata.getDouble("bigDecimal")).isEqualTo(4.5d);
    }

    @Test
    void should_fall_back_to_a_double_for_a_big_integer_that_does_not_fit_a_long() throws Exception {
        final BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        final Metadata metadata = createMetadata(Collections.singletonMap("bigInteger", tooBig));

        assertThat(metadata.getDouble("bigInteger")).isEqualTo(tooBig.doubleValue());
    }

    @Test
    void should_reject_a_value_of_an_unsupported_type() {
        assertThatThrownBy(() -> createMetadata(Collections.singletonMap("boolean", Boolean.TRUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported type")
                .hasMessageContaining("java.lang.Boolean");

        assertThatThrownBy(() -> createMetadata(Collections.singletonMap("object", Map.of("nested", "value"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported type");
    }

    @Test
    void should_reject_a_null_value() {
        assertThatThrownBy(() -> createMetadata(Collections.singletonMap("missing", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }
}
