package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agentic.Agents;
import org.junit.jupiter.api.Test;

public class AgenticScopeJsonSerializationIT {

    @Test
    void agenticScope_pojo_serialization_test() {
        DefaultAgenticScope agenticScope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);

        Person person = new Person();
        person.setName("Mario");
        person.setAge(51);
        person.setAdult(true);

        agenticScope.writeState("category", Agents.RequestCategory.MEDICAL);
        agenticScope.writeState("person", person);

        String json = AgenticScopeSerializer.toJson(agenticScope);
        System.out.println(json);
        assertThat(json).contains("is_adult");
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.memoryId()).isEqualTo(agenticScope.memoryId());
        assertThat(deserialized.readState("category")).isEqualTo(Agents.RequestCategory.MEDICAL);

        Person deserPerson = (Person) deserialized.readState("person");
        assertThat(deserPerson.getName()).isEqualTo("Mario");
        assertThat(deserPerson.getAge()).isEqualTo(51);
        assertThat(deserPerson.isAdult()).isTrue();
    }

    public static class EvilGadget {
        public EvilGadget() throws Exception {
            System.out.println("PWNED! Arbitrary Class Instantiated!");
        }
    }

    @Test
    void agenticScope_secure_serialization_test() throws Exception {
        // Payload targeting the vulnerable codec
        String payload = "[\"" + DefaultAgenticScope.class.getName() + "\", {" + "\"state\": [\"java.util.HashMap\", {"
                + "  \"exploit\": [\""
                + EvilGadget.class.getName() + "\", {}]" + "}]"
                + "}]";

        Class<?> codecClass = JacksonAgenticScopeJsonCodec.class;
        var constructor = codecClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object codecInstance = constructor.newInstance();

        var method = codecClass.getDeclaredMethod("fromJson", String.class);
        method.setAccessible(true);
        assertThat(assertThrows(Exception.class, () -> method.invoke(codecInstance, payload)))
                .cause()
                .hasMessageContaining("Failed to deserialize");
    }
}
