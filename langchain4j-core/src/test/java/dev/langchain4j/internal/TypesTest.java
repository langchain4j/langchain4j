package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypesTest {

    record Person(String name, int age) {}

    private final Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder().build());

    @Test
    void reads_a_list_of_a_generic_type() {
        List<Person> people = codec.fromJson("[{\"name\":\"Ada\",\"age\":36}]", Types.listOf(Person.class));

        assertThat(people).containsExactly(new Person("Ada", 36));
    }

    @Test
    void reads_a_parameterized_type() {
        Map<String, Person> byName = codec.fromJson(
                "{\"ada\":{\"name\":\"Ada\",\"age\":36}}", Types.parameterized(Map.class, String.class, Person.class));

        assertThat(byName).containsEntry("ada", new Person("Ada", 36));
    }

    @Test
    void does_not_expose_its_argument_array() {
        java.lang.reflect.ParameterizedType type =
                (java.lang.reflect.ParameterizedType) Types.parameterized(Map.class, String.class, Person.class);

        type.getActualTypeArguments()[0] = Integer.class;

        assertThat(type.getActualTypeArguments()[0]).isEqualTo(String.class);
    }
}
