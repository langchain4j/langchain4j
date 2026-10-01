package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Jackson 2 reads and writes an enum by {@code name()}. Jackson 3 uses {@code toString()} by default,
 * so an enum that overrides it would silently change on the wire, and a value written by name -
 * as LangChain4j's JSON schemas list enum values, and as persisted data holds them - would no
 * longer be readable.
 */
class Jackson3EnumParityTest {

    private static final Json.JsonCodec PROVIDER_CODEC =
            ProviderJson.codec(ProviderJsonSpec.builder().build());

    enum Priority {
        HIGH,
        LOW;

        @Override
        public String toString() {
            return "Priority " + name().toLowerCase(Locale.ROOT);
        }
    }

    static class Task {
        public Priority priority;
    }

    @Test
    void reads_enum_by_name() {
        assertThat(Json.fromJson("{\"priority\":\"HIGH\"}", Task.class).priority).isEqualTo(Priority.HIGH);
    }

    @Test
    void writes_enum_by_name() {
        Task task = new Task();
        task.priority = Priority.LOW;

        assertThat(Json.toJson(task)).isEqualTo("{\"priority\":\"LOW\"}");
    }

    @Test
    void provider_codec_reads_enum_by_name() {
        assertThat(PROVIDER_CODEC.fromJson("{\"priority\":\"HIGH\"}", Task.class).priority)
                .isEqualTo(Priority.HIGH);
    }

    @Test
    void provider_codec_writes_enum_by_name() {
        Task task = new Task();
        task.priority = Priority.LOW;

        assertThat(PROVIDER_CODEC.toJson(task)).isEqualTo("{\"priority\":\"LOW\"}");
    }
}
