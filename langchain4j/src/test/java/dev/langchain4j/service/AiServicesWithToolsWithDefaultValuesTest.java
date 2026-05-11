package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServicesWithToolsWithDefaultValuesTest {

    @Captor
    ArgumentCaptor<ChatRequest> chatRequestCaptor;

    interface Assistant {
        String chat(String userMessage);
    }

    private static ChatModel mockReturningToolCallWithArgs(String toolName, String arguments) {
        return ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name(toolName)
                        .arguments(arguments)
                        .build()),
                AiMessage.from("Done"));
    }

    // ---------------------------------------------------------------------
    // Primitives
    // ---------------------------------------------------------------------

    @Test
    void should_substitute_default_for_primitive_int_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "10") int limit) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(10);
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_substitute_default_for_primitive_double_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "3.14") double pi) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(3.14);
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_substitute_default_for_primitive_boolean_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "true") boolean verbose) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(true);
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_substitute_default_for_primitive_long_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "9999999999") long bigNumber) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(9999999999L);
        verifyNoMoreInteractions(tool);
    }

    // ---------------------------------------------------------------------
    // Boxed / object scalar types
    // ---------------------------------------------------------------------

    @Test
    void should_substitute_default_for_boxed_Integer_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "42") Integer limit) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(42);
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_substitute_default_for_String_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "USD") String currency) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process("USD");
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_substitute_default_for_BigDecimal_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "1.5") BigDecimal value) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(BigDecimal.valueOf(1.5));
        verifyNoMoreInteractions(tool);
    }

    // ---------------------------------------------------------------------
    // Enums
    // ---------------------------------------------------------------------

    enum Currency {
        USD,
        EUR,
        GBP
    }

    @Test
    void should_substitute_default_for_enum_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "EUR") Currency currency) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(Currency.EUR);
        verifyNoMoreInteractions(tool);
    }

    // ---------------------------------------------------------------------
    // POJOs (including nested)
    // ---------------------------------------------------------------------

    public record Address(String city, String country) {}

    public record Person(String name, int age, Address address) {}

    @Test
    void should_substitute_default_for_POJO_with_nested_POJO_when_LLM_omits_it() {
        class Tools {

            public static final String DEFAULT_PERSON = """
                    {"name":"Klaus","age":42,"address":{"city":"Berlin","country":"DE"}}""";

            @Tool
            void process(@P(defaultValue = DEFAULT_PERSON) Person person) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(new Person("Klaus", 42, new Address("Berlin", "DE")));
        verifyNoMoreInteractions(tool);
    }

    // ---------------------------------------------------------------------
    // Polymorphic types
    // ---------------------------------------------------------------------

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Cat.class, name = "cat"),
        @JsonSubTypes.Type(value = Dog.class, name = "dog")
    })
    public sealed interface Animal permits Cat, Dog {}

    public record Cat(String name) implements Animal {}

    public record Dog(String breed) implements Animal {}

    @Test
    void should_substitute_default_for_polymorphic_sealed_type_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "{\"type\":\"cat\",\"name\":\"Whiskers\"}") Animal animal) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(new Cat("Whiskers"));
        verifyNoMoreInteractions(tool);
    }

    // ---------------------------------------------------------------------
    // Collections and maps
    // ---------------------------------------------------------------------

    @Test
    void should_substitute_default_for_List_of_strings_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "[\"red\",\"green\",\"blue\"]") List<String> colors) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(List.of("red", "green", "blue"));
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_substitute_default_for_Set_of_integers_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "[1,2,3]") Set<Integer> ids) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(new LinkedHashSet<>(List.of(1, 2, 3)));
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_substitute_default_for_Map_when_LLM_omits_it() {
        class Tools {
            @Tool
            void process(@P(value = "string keys to integer values", defaultValue = "{\"a\":1,\"b\":2}")
                    Map<String, Integer> counts) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("a", 1);
        expected.put("b", 2);
        verify(tool).process(expected);
        verifyNoMoreInteractions(tool);
    }

    // ---------------------------------------------------------------------
    // Corner cases
    // ---------------------------------------------------------------------

    @Test
    void should_use_LLM_provided_value_over_default() {
        class Tools {
            @Tool
            void process(@P(defaultValue = "10") int limit) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("process", "{\"arg0\":42}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(42);
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_mix_defaulted_and_LLM_provided_parameters() {
        class Tools {
            @Tool
            void search(String query, @P(defaultValue = "10") int limit, @P(defaultValue = "0") int offset) {}
        }
        Tools tool = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockReturningToolCallWithArgs("search", "{\"arg0\":\"hello\"}"))
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).search("hello", 10, 0);
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_apply_default_even_when_required_is_explicitly_true() {
        // defaultValue forces the schema to mark the param as optional
        // (so the LLM is told it can omit it), and supplies the runtime fallback.
        class Tools {
            @Tool
            void process(@P(required = true, defaultValue = "10") int limit) {}
        }
        Tools tool = spy(new Tools());

        ChatModel chatModelMock = mockReturningToolCallWithArgs("process", "{}");
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModelMock)
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(tool).process(10);
        verifyNoMoreInteractions(tool);
    }

    @Test
    void should_advertise_defaulted_parameter_as_optional_in_JSON_schema() {
        // Capture the schema actually sent to the LLM and verify the defaulted param
        // is not in the "required" array.
        class Tools {
            @Tool
            void process(String mandatory, @P(defaultValue = "10") int withDefault) {}
        }
        Tools tool = spy(new Tools());

        ChatModel chatModelMock = org.mockito.Mockito.spy(
                mockReturningToolCallWithArgs("process", "{\"arg0\":\"hi\"}"));
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModelMock)
                .tools(tool)
                .build();

        assistant.chat("call the tool");

        verify(chatModelMock, org.mockito.Mockito.atLeastOnce()).chat(chatRequestCaptor.capture());
        ToolSpecification spec = chatRequestCaptor.getValue().parameters().toolSpecifications().get(0);
        assertThat(spec.parameters().required()).containsExactly("arg0");
        assertThat(spec.parameters().properties().keySet()).containsExactly("arg0", "arg1");
    }
}
