package dev.langchain4j.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesWithNewToolsWithDescriptionIT.ToolWithEnumParameter.TemperatureUnit.CELSIUS;
import static dev.langchain4j.service.AiServicesWithNewToolsWithDescriptionIT.ToolWithListOfEnumsParameter.Color.GREEN;
import static dev.langchain4j.service.AiServicesWithNewToolsWithDescriptionIT.ToolWithListOfEnumsParameter.Color.RED;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AiServicesWithNewToolsWithDescriptionIT {

    @Captor
    private ArgumentCaptor<List<ToolSpecification>> toolSpecificationCaptor;

    private static List<ChatLanguageModel> models() {
        return singletonList(OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    // TODO single argument: List/Set/Array of primitives, List/Set/Array of enums, List/Set/Array of POJOs, map?
    // TODO up-wrap single POJO and Map? (remove one level of object nesting) Make sure descriptions still work.

    interface Assistant {

        Response<AiMessage> chat(String userMessage);
    }

    static class ToolWithPrimitiveParameters {

        @Tool("adds two numbers")
        int add(@P("first number") int a, @P("second number") int b) {
            return a + b;
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .addIntegerProperty("arg0", i -> i.description("first number"))
                .addIntegerProperty("arg1", i -> i.description("second number"))
                .required("arg0", "arg1")
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_primitive_parameters(ChatLanguageModel model) {

        // given
        model = spy(model);

        ToolWithPrimitiveParameters tool = spy(new ToolWithPrimitiveParameters());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .build();

        String text = "How much is 37 plus 87?";

        // when
        Response<AiMessage> response = assistant.chat(text);

        // then
        assertThat(response.content().text()).contains("124");

        verify(tool).add(37, 87);
        verifyNoMoreInteractions(tool);

        verify(model).supportedCapabilities();
        verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
        verifyNoMoreInteractions(model);

        List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("add");
        assertThat(toolSpecification.description()).isEqualTo("adds two numbers");
        assertThat(toolSpecification.parameters()).isEqualTo(ToolWithPrimitiveParameters.EXPECTED_SCHEMA);
    }

    static class ToolWithPojoParameter {

        @ToString
        @AllArgsConstructor
        @EqualsAndHashCode
        @Description("a person")
        static class Person {

            @Description("a name")
            String name;

            @Description("an age")
            int age;

            @Description("a height")
            Double height;

            @Description("is married")
            boolean married;
        }

        @Tool("processes a person")
        void process(@P("a person 2") Person person) {
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .properties(singletonMap("arg0", JsonObjectSchema.builder()
                        .description("a person 2")
                        .addStringProperty("name", s -> s.description("a name"))
                        .addIntegerProperty("age", i -> i.description("an age"))
                        .addNumberProperty("height", n -> n.description("a height"))
                        .addBooleanProperty("married", b -> b.description("is married"))
                        .required("name", "age", "height", "married")
                        .build()))
                .required("arg0")
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_pojo_with_primitives(ChatLanguageModel model) {

        // given
        model = spy(model);

        ToolWithPojoParameter tool = spy(new ToolWithPojoParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .build();

        String text = "Use 'process' tool to process the following: Klaus is 37 years old, 1.78m height and single";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(new ToolWithPojoParameter.Person("Klaus", 37, 1.78, false));
        verifyNoMoreInteractions(tool);

        verify(model).supportedCapabilities();
        verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
        verifyNoMoreInteractions(model);

        List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("process");
        assertThat(toolSpecification.description()).isEqualTo("processes a person");
        assertThat(toolSpecification.parameters()).isEqualTo(ToolWithPojoParameter.EXPECTED_SCHEMA);
    }

    static class ToolWithNestedPojoParameter {

        @ToString
        @AllArgsConstructor
        @EqualsAndHashCode
        @Description("a person")
        static class Person {

            @Description("a name")
            String name;

            @Description("an address 2")
            Address address;
        }

        @ToString
        @AllArgsConstructor
        @EqualsAndHashCode
        @Description("an address")
        static class Address {

            @Description("a city")
            String city;
        }

        @Tool("processes a person")
        void process(@P("a person 2") Person person) {
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .properties(singletonMap("arg0", JsonObjectSchema.builder()
                        .description("a person 2")
                        .addStringProperty("name", s -> s.description("a name"))
                        .addProperty("address", JsonObjectSchema.builder()
                                .description("an address 2")
                                .addStringProperty("city", s -> s.description("a city"))
                                .required("city")
                                .build())
                        .required("name", "address")
                        .build()))
                .required("arg0")
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_pojo_with_nested_pojo(ChatLanguageModel model) {

        // given
        model = spy(model);

        ToolWithNestedPojoParameter tool = spy(new ToolWithNestedPojoParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .build();

        String text = "Use 'process' tool to process the following: Klaus lives in Langley Falls";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(new ToolWithNestedPojoParameter.Person("Klaus", new ToolWithNestedPojoParameter.Address("Langley Falls")));
        verifyNoMoreInteractions(tool);

        verify(model).supportedCapabilities();
        verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
        verifyNoMoreInteractions(model);

        List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("process");
        assertThat(toolSpecification.description()).isEqualTo("processes a person");
        assertThat(toolSpecification.parameters()).isEqualTo(ToolWithNestedPojoParameter.EXPECTED_SCHEMA);
    }

    static class ToolWithRecursion {

        @ToString
        @AllArgsConstructor
        @EqualsAndHashCode
        @Description("a person")
        static class Person {

            @Description("a name")
            String name;

            @Description("a list of person")
            List<Person> children;
        }

        @Tool("processes a person")
        void process(@P("a person 2") Person person) {
        }

        static final String REFERENCE = generateUUIDFrom(Person.class.getName());

        static final JsonObjectSchema PERSON_SCHEMA = JsonObjectSchema.builder()
                .description("a person 2")
                .addStringProperty("name", s -> s.description("a name"))
                .addArrayProperty("children", a -> a.description("a list of person").items(JsonReferenceSchema.builder()
                                .reference(REFERENCE)
                                .build())
                        .build())
                .required("name", "children")
                .build();

        static final JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .addProperty("arg0", PERSON_SCHEMA)
                .required("arg0")
                .definitions(singletonMap(REFERENCE, PERSON_SCHEMA))
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_pojo_with_recursion(ChatLanguageModel model) {

        // given
        model = spy(model);

        ToolWithRecursion tool = spy(new ToolWithRecursion());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .build();

        String text = "Use 'process' tool to process the following: Francine has 2 children: Steve and Hayley";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(
                new ToolWithRecursion.Person(
                        "Francine",
                        asList(
                                new ToolWithRecursion.Person("Steve", emptyList()),
                                new ToolWithRecursion.Person("Hayley", emptyList())
                        )
                )
        );
        verifyNoMoreInteractions(tool);

        verify(model).supportedCapabilities();
        verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
        verifyNoMoreInteractions(model);

        List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("process");
        assertThat(toolSpecification.description()).isEqualTo("processes a person");
        assertThat(toolSpecification.parameters()).isEqualTo(ToolWithRecursion.EXPECTED_SCHEMA);
    }

    static class ToolWithEnumParameter {

        @Description("a temperature unit")
        enum TemperatureUnit {

            CELSIUS, fahrenheit, Kelvin
        }

        @Tool("returns current temperature")
        int currentTemperature(String city, @P("a temperature unit 2") TemperatureUnit unit) {
            return 19;
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("currentTemperature")
                .description("returns current temperature")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("arg0", new JsonStringSchema())
                        .addProperty("arg1", JsonEnumSchema.builder()
                                .description("a temperature unit 2")
                                .enumValues("CELSIUS", "fahrenheit", "Kelvin")
                                .build())
                        .required("arg0", "arg1")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_enum_parameter(ChatLanguageModel model) {

        // given
        model = spy(model);

        ToolWithEnumParameter tool = spy(new ToolWithEnumParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .build();

        String text = "What is the weather in Munich in celsius?";

        // when
        Response<AiMessage> response = assistant.chat(text);

        // then
        assertThat(response.content().text()).contains("19");

        verify(tool).currentTemperature("Munich", CELSIUS);
        verifyNoMoreInteractions(tool);

        verify(model).supportedCapabilities();
        verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
        verifyNoMoreInteractions(model);

        List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
        assertThat(toolSpecifications).hasSize(1);
        assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithEnumParameter.EXPECTED_SPECIFICATION);
    }

    static class ToolWithMapParameter {

        @Tool("processes ages")
        void process(@P("map from name to age") Map<String, Integer> ages) {
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("process")
                .description("processes ages")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("arg0", JsonObjectSchema.builder()
                                .description("map from name to age")
                                .build())
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_map_parameter(ChatLanguageModel model) {

        // given
        model = spy(model);

        ToolWithMapParameter tool = spy(new ToolWithMapParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .build();

        String text = "Process the following: Klaus is 42 years old and Francine is 47 years old";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(new HashMap<String, Integer>() {{
            put("Klaus", 42);
            put("Francine", 47);
        }});
        verifyNoMoreInteractions(tool);

        verify(model).supportedCapabilities();
        verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
        verifyNoMoreInteractions(model);

        List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
        assertThat(toolSpecifications).hasSize(1);
        assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithMapParameter.EXPECTED_SPECIFICATION);
    }

    static class ToolWithListOfEnumsParameter {

        @Description("a color")
        enum Color {

            RED, GREEN, BLUE
        }

        @Tool("processes colors")
        void process(@P("a list of colors") List<Color> colors) {
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("process")
                .description("processes colors")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("arg0", JsonArraySchema.builder()
                                .description("a list of colors")
                                .items(JsonEnumSchema.builder()
                                        .description("a color")
                                        .enumValues("RED", "GREEN", "BLUE")
                                        .build())
                                .build())
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_list_of_enums_parameter(ChatLanguageModel model) {

        // given
        model = spy(model);

        ToolWithListOfEnumsParameter tool = spy(new ToolWithListOfEnumsParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tool)
                .build();

        String text = "Process the following colors: RED and GREEN";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(asList(RED, GREEN));
        verifyNoMoreInteractions(tool);

        verify(model).supportedCapabilities();
        verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
        verifyNoMoreInteractions(model);

        List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
        assertThat(toolSpecifications).hasSize(1);
        assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithListOfEnumsParameter.EXPECTED_SPECIFICATION);
    }
}
