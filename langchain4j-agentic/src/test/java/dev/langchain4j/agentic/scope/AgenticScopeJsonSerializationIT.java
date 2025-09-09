package dev.langchain4j.agentic.scope;

import dev.langchain4j.agentic.Agents;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AgenticScopeJsonSerializationIT {

    @Test
    void agenticScope_pojo_serialization_test() {
        DefaultAgenticScope agenticScope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);

        Person person = new Person();
        person.setName("Mario");
        person.setAge(51);

        agenticScope.writeState("category", Agents.RequestCategory.MEDICAL);
        agenticScope.writeState("person", person);

        String json = AgenticScopeSerializer.toJson(agenticScope);
        System.out.println(json);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.memoryId()).isEqualTo(agenticScope.memoryId());
        assertThat(deserialized.readState("category")).isEqualTo(Agents.RequestCategory.MEDICAL);

        Person deserPerson = (Person) deserialized.readState("person");
        assertThat(deserPerson.getName()).isEqualTo("Mario");
        assertThat(deserPerson.getAge()).isEqualTo(51);
    }
}
