package dev.langchain4j.service.common;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.ToolWithEnumParameter.TemperatureUnit.CELSIUS;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.ToolWithSetOfEnumsParameter.Color.GREEN;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.ToolWithSetOfEnumsParameter.Color.RED;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@TestInstance(PER_CLASS)
@ExtendWith(MockitoExtension.class)
public abstract class AbstractAiServiceWithToolsIT {
    // TODO test the same for streaming models

    @Captor
    private ArgumentCaptor<ChatRequest> chatRequestCaptor;

    protected abstract List<ChatModel> models();

    protected List<ChatModel> modelsSupportingMapParametersInTools() {
        return models();
    }

    // TODO test token usage is summed for tools
    // TODO single argument: array of primitives, array of enums, array of POJOs, map?
    // TODO up-wrap single POJO and Map? (remove one level of object nesting) Make sure descriptions still work.

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    static class ToolWithPrimitiveParameters {

        @Tool
        int add(int a, int b) {
            return a + b;
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .addIntegerProperty("arg0")
                .addIntegerProperty("arg1")
                .required("arg0", "arg1")
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_primitive_parameters(ChatModel model) {

        // given
        model = spy(model);

        ToolWithPrimitiveParameters tool = spy(new ToolWithPrimitiveParameters());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "How much is 37 plus 87?";

        // when
        Result<String> result = assistant.chat(text);

        // then
        assertThat(result.content()).contains("124");

        verify(tool).add(37, 87);
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            ToolSpecification toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("add");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isEqualTo(ToolWithPrimitiveParameters.EXPECTED_SCHEMA);
        }
    }

    static class ToolWithPojoParameter {

        record Person(String name, int age, Double height, boolean married) {
        }

        @Tool
        void process(Person person) {
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .addProperty("arg0", JsonObjectSchema.builder()
                        .addStringProperty("name")
                        .addIntegerProperty("age")
                        .addNumberProperty("height")
                        .addBooleanProperty("married")
                        .required("name", "age", "height", "married")
                        .build())
                .required("arg0")
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_pojo_with_primitives(ChatModel model) {

        // given
        model = spy(model);

        ToolWithPojoParameter tool = spy(new ToolWithPojoParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Use 'process' tool to process the following: Klaus is 37 years old, 1.78m height and single";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(new ToolWithPojoParameter.Person("Klaus", 37, 1.78, false));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            ToolSpecification toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("process");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isEqualTo(ToolWithPojoParameter.EXPECTED_SCHEMA);
        }
    }

    static class ToolWithNestedPojoParameter {

        record Person(String name, Address address) {
        }

        record Address(String city) {
        }

        @Tool
        void process(Person person) {
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .addProperty("arg0", JsonObjectSchema.builder()
                        .addProperty("name", new JsonStringSchema())
                        .addProperty(
                                "address",
                                JsonObjectSchema.builder()
                                        .addProperty("city", new JsonStringSchema())
                                        .required("city")
                                        .build())
                        .required("name", "address")
                        .build())
                .required("arg0")
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_pojo_with_nested_pojo(ChatModel model) {

        // given
        model = spy(model);

        ToolWithNestedPojoParameter tool = spy(new ToolWithNestedPojoParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Use 'process' tool to process the following: Klaus lives in Langley Falls";

        // when
        assistant.chat(text);

        // then
        verify(tool)
                .process(new ToolWithNestedPojoParameter.Person(
                        "Klaus", new ToolWithNestedPojoParameter.Address("Langley Falls")));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            ToolSpecification toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("process");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isEqualTo(ToolWithNestedPojoParameter.EXPECTED_SCHEMA);
        }
    }

    static class ToolWithRecursion {

        record Person(String name, List<Person> children) {
        }

        @Tool
        void process(Person person) {
        }

        static final String REFERENCE = generateUUIDFrom(ToolWithRecursion.Person.class.getName());

        static final JsonObjectSchema PERSON_SCHEMA = JsonObjectSchema.builder()
                .addStringProperty("name")
                .addProperty("children", JsonArraySchema.builder()
                        .items(JsonReferenceSchema.builder()
                                .reference(REFERENCE)
                                .build())
                        .build())
                .required("name", "children")
                .build();

        static final JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .addProperties(singletonMap("arg0", PERSON_SCHEMA))
                .required("arg0")
                .definitions(singletonMap(REFERENCE, PERSON_SCHEMA))
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsRecursion")
    void should_execute_tool_with_pojo_with_recursion(ChatModel model) {

        // given
        model = spy(model);

        ToolWithRecursion tool = spy(new ToolWithRecursion());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Use 'process' tool to process the following: Francine has 2 children: Steve and Hayley";

        // when
        assistant.chat(text);

        // then
        verify(tool)
                .process(new ToolWithRecursion.Person(
                        "Francine",
                        asList(
                                new ToolWithRecursion.Person("Steve", emptyList()),
                                new ToolWithRecursion.Person("Hayley", emptyList()))));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            ToolSpecification toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("process");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isEqualTo(ToolWithRecursion.EXPECTED_SCHEMA);
        }
    }

    protected boolean supportsRecursion() {
        return false;
    }

    static class ToolWithoutParameters {

        @Tool
        LocalTime currentTime() {
            return LocalTime.of(17, 11, 45);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_without_parameters(ChatModel model) {

        // given
        model = spy(model);

        ToolWithoutParameters tools = spy(new ToolWithoutParameters());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        String text = "What is the time now? Respond in HH:MM:SS format.";

        // when
        Result<String> result = assistant.chat(text);

        // then
        assertThat(result.content()).contains("17:11:45");

        verify(tools).currentTime();
        verifyNoMoreInteractions(tools);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            ToolSpecification toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("currentTime");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isNull();
        }
    }

    static class ToolWithEnumParameter {

        enum TemperatureUnit {
            CELSIUS,
            fahrenheit,
            Kelvin
        }

        @Tool
        int currentTemperature(String city, TemperatureUnit unit) {
            return 19;
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("currentTemperature")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("arg0", new JsonStringSchema())
                        .addProperty(
                                "arg1",
                                JsonEnumSchema.builder()
                                        .enumValues("CELSIUS", "fahrenheit", "Kelvin")
                                        .build())
                        .required("arg0", "arg1")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_enum_parameter(ChatModel model) {

        // given
        model = spy(model);

        ToolWithEnumParameter tool = spy(new ToolWithEnumParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "What is the current temperature in Munich in celsius?";

        // when
        Result<String> result = assistant.chat(text);

        // then
        assertThat(result.content()).contains("19");

        verify(tool).currentTemperature("Munich", CELSIUS);
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithEnumParameter.EXPECTED_SPECIFICATION);
        }
    }

    static class ToolWithMapParameter {

        @Tool
        void process(@P("map from name to age") Map<String, Integer> ages) {
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("process")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "arg0",
                                JsonObjectSchema.builder()
                                        .description("map from name to age")
                                        .build())
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingMapParametersInTools")
    @EnabledIf("supportsMapParameters")
    void should_execute_tool_with_map_parameter(ChatModel model) {

        // given
        model = spy(model);

        ToolWithMapParameter tool = spy(new ToolWithMapParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Process the following: Klaus is 42 years old and Francine is 47 years old";

        // when
        assistant.chat(text);

        // then
        verify(tool)
                .process(Map.of(
                        "Klaus", 42,
                        "Francine", 47));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithMapParameter.EXPECTED_SPECIFICATION);
        }
    }

    protected boolean supportsMapParameters() {
        return true;
    }

    static class ToolWithListOfStringsParameter {

        @Tool
        void processNames(List<String> names) {
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("processNames")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "arg0",
                                JsonArraySchema.builder()
                                        .items(new JsonStringSchema())
                                        .build())
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_list_of_strings_parameter(ChatModel model) {

        // given
        model = spy(model);

        ToolWithListOfStringsParameter tool = spy(new ToolWithListOfStringsParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Process the following names: Klaus and Franny";

        // when
        assistant.chat(text);

        // then
        verify(tool).processNames(asList("Klaus", "Franny"));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithListOfStringsParameter.EXPECTED_SPECIFICATION);
        }
    }

    static class ToolWithSetOfEnumsParameter {

        enum Color {
            RED,
            GREEN,
            BLUE
        }

        @Tool
        void process(Set<Color> colors) {
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("process")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "arg0",
                                JsonArraySchema.builder()
                                        .items(JsonEnumSchema.builder()
                                                .enumValues("RED", "GREEN", "BLUE")
                                                .build())
                                        .build())
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_with_set_of_enums_parameter(ChatModel model) {

        // given
        model = spy(model);

        ToolWithSetOfEnumsParameter tool = spy(new ToolWithSetOfEnumsParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Process the following colors: RED and GREEN";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(Set.of(RED, GREEN));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithSetOfEnumsParameter.EXPECTED_SPECIFICATION);
        }
    }

    static class ToolWithCollectionOfIntegersParameter {

        @Tool
        void processNumbers(Collection<Integer> names) {
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("processNumbers")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "arg0",
                                JsonArraySchema.builder()
                                        .items(new JsonIntegerSchema())
                                        .build())
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_collection_of_integers_parameter(ChatModel model) {

        // given
        model = spy(model);

        ToolWithCollectionOfIntegersParameter tool = spy(new ToolWithCollectionOfIntegersParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Process the following integers: 37, 73";

        // when
        assistant.chat(text);

        // then
        verify(tool).processNumbers(List.of(37, 73));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            assertThat(toolSpecifications.get(0))
                    .isEqualTo(ToolWithCollectionOfIntegersParameter.EXPECTED_SPECIFICATION);
        }
    }

    static class ToolWithListOfPojoParameter {

        record Person(String name) {
        }

        @Tool
        void process(List<Person> people) {
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("process")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "arg0",
                                JsonArraySchema.builder()
                                        .items(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .required("name")
                                                .build())
                                        .build())
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_list_of_POJOs_parameter(ChatModel model) {

        // given
        model = spy(model);

        ToolWithListOfPojoParameter tool = spy(new ToolWithListOfPojoParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Process the following people: Klaus and Franny";

        // when
        assistant.chat(text);

        // then
        try {
            verify(tool)
                    .process(List.of(
                            new ToolWithListOfPojoParameter.Person("Klaus"),
                            new ToolWithListOfPojoParameter.Person("Franny")));
        } catch (Throwable t) {
            verify(tool).process(List.of(new ToolWithListOfPojoParameter.Person("Klaus")));
            verify(tool).process(List.of(new ToolWithListOfPojoParameter.Person("Franny")));
        }
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithListOfPojoParameter.EXPECTED_SPECIFICATION);
        }
    }

    protected boolean verifyModelInteractions() {
        return false;
    }

    static class ToolWithUUIDParameter {

        Map<UUID, String> usernames = Map.of(
                UUID.fromString("62dbcc27-aaf3-449a-b12d-5a904271a57f"), "Alice",
                UUID.fromString("d1dbd3c2-25ab-4b10-b4f0-70c34088a248"), "Bob");

        @Tool
        String getUsernameFromId(UUID id) {
            return usernames.get(id);
        }

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("getUsernameFromId")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("arg0", "String in a UUID format")
                        .required("arg0")
                        .build())
                .build();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_uuid_parameter(ChatModel model) {

        // given
        model = spy(model);

        ToolWithUUIDParameter tool = spy(new ToolWithUUIDParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "What is the username with ID 62dbcc27-aaf3-449a-b12d-5a904271a57f?";

        // when
        Result<String> result = assistant.chat(text);

        // then
        assertThat(result.content()).contains("Alice");

        verify(tool).getUsernameFromId(UUID.fromString("62dbcc27-aaf3-449a-b12d-5a904271a57f"));
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model).supportedCapabilities();
            verify(model, times(2)).chat(chatRequestCaptor.capture());
            verifyNoMoreInteractionsFor(model);

            List<ToolSpecification> toolSpecifications =
                    chatRequestCaptor.getValue().parameters().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            assertThat(toolSpecifications.get(0)).isEqualTo(ToolWithUUIDParameter.EXPECTED_SPECIFICATION);
        }
    }
}
