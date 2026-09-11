package dev.langchain4j.agentic.scope;

import dev.langchain4j.agentic.Agents;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AgenticScopeJsonSerializationIT {

    @Test
    void agenticScope_pojo_serialization_test() {
        AgenticScopeSerializer.allowDeserializationType(Person.class);

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

    public static class ConstructorBomb {
        public ConstructorBomb() {
            throw new SecurityException("Arbitrary class instantiated during deserialization");
        }
    }

    @Test
    void agenticScope_should_reject_arbitrary_class_in_state() {
        // ConstructorBomb is NOT in the allowlist, so the type validator
        // blocks it before instantiation — the constructor never runs.
        String payload = "{" +
                "\"memoryId\": \"test-id\"," +
                "\"kind\": \"PERSISTENT\"," +
                "\"state\": [\"java.util.concurrent.ConcurrentHashMap\", {" +
                "  \"exploit\": [\"" + ConstructorBomb.class.getName() + "\", {}]" +
                "}]," +
                "\"agentInvocations\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]," +
                "\"context\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]" +
                "}";

        UnserializableAgenticScopeException ex = assertThrows(UnserializableAgenticScopeException.class,
                () -> AgenticScopeSerializer.fromJson(payload));

        assertThat(ex.getMessage())
                .contains(ConstructorBomb.class.getName())
                .contains("allowDeserializationType");
    }

    @Test
    void agenticScope_should_reject_array_wrapped_class_in_state() {
        // Same gadget as above, but smuggled as an array type id ("[L...;"): the element type must
        // still be validated, so a disallowed class cannot be instantiated by wrapping it in an array.
        String payload = "{" +
                "\"memoryId\": \"test-id\"," +
                "\"kind\": \"PERSISTENT\"," +
                "\"state\": [\"java.util.concurrent.ConcurrentHashMap\", {" +
                "  \"exploit\": [\"[L" + ConstructorBomb.class.getName() + ";\", [ {} ]]" +
                "}]," +
                "\"agentInvocations\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]," +
                "\"context\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]" +
                "}";

        UnserializableAgenticScopeException ex = assertThrows(UnserializableAgenticScopeException.class,
                () -> AgenticScopeSerializer.fromJson(payload));

        assertThat(ex.getMessage()).contains(ConstructorBomb.class.getName());
    }

    @Test
    void agenticScope_should_reject_unregistered_type() {
        String payload = "{" +
                "\"memoryId\": \"test-id\"," +
                "\"kind\": \"PERSISTENT\"," +
                "\"state\": [\"java.util.concurrent.ConcurrentHashMap\", {" +
                "  \"exploit\": [\"javax.naming.InitialContext\", {}]" +
                "}]," +
                "\"agentInvocations\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]," +
                "\"context\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]" +
                "}";

        UnserializableAgenticScopeException ex = assertThrows(UnserializableAgenticScopeException.class,
                () -> AgenticScopeSerializer.fromJson(payload));

        assertThat(ex.getMessage())
                .contains("javax.naming.InitialContext")
                .contains("allowDeserializationType");
    }

    @Test
    void agenticScope_should_allow_registered_user_type() {
        AgenticScopeSerializer.allowDeserializationType(Person.class);

        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        Person person = new Person();
        person.setName("Mario");
        person.setAge(51);
        person.setAdult(true);
        scope.writeState("person", person);

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        Person p = (Person) deserialized.readState("person");
        assertThat(p.getName()).isEqualTo("Mario");
        assertThat(p.getAge()).isEqualTo(51);
        assertThat(p.isAdult()).isTrue();
    }

    @Test
    void agenticScope_should_reject_class_from_non_allowed_langchain4j_package() {
        String payload = "{" +
                "\"memoryId\": \"test-id\"," +
                "\"kind\": \"PERSISTENT\"," +
                "\"state\": [\"java.util.concurrent.ConcurrentHashMap\", {" +
                "  \"exploit\": [\"" + AgenticScopeSerializer.class.getName() + "\", {}]" +
                "}]," +
                "\"agentInvocations\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]," +
                "\"context\": [\"java.util.Collections$SynchronizedRandomAccessList\", []]" +
                "}";

        UnserializableAgenticScopeException ex = assertThrows(UnserializableAgenticScopeException.class,
                () -> AgenticScopeSerializer.fromJson(payload));

        assertThat(ex.getMessage())
                .contains(AgenticScopeSerializer.class.getName())
                .contains("allowDeserializationType");
    }
}
