package dev.langchain4j.service.common;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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
import dev.langchain4j.service.IllegalConfigurationException;
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

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.ToolWithEnumParameter.TemperatureUnit.CELSIUS;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.ToolWithSetOfEnumsParameter.Color.GREEN;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.ToolWithSetOfEnumsParameter.Color.RED;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.argThat;
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

    static JsonSchemaElement PRIMITIVE_TOOL_EXPECTED_SCHEMA = JsonObjectSchema.builder()
            .addIntegerProperty("arg0")
            .addIntegerProperty("arg1")
            .required("arg0", "arg1")
            .build();

    interface AdderTool {
        int add(int a, int b);
    }

    static class ToolWithPrimitiveParameters implements AdderTool {

        @Tool
        @Override
        public int add(int a, int b) {
            return a + b;
        }
    }

    static class ImmediateToolWithPrimitiveParameters implements AdderTool {

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public int add(int a, int b) {
            return a + b;
        }
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

        String text = adaptPrompt2("How much is 37 plus 87?");

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
            assertThat(toolSpecification.parameters()).isEqualTo(PRIMITIVE_TOOL_EXPECTED_SCHEMA);
        }
    }

    protected String adaptPrompt2(String prompt) {
        return prompt;
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

        static final String REFERENCE = generateUUIDFrom(Person.class.getName());

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
        verify(tool).process(argThat(person -> person.name().equals("Francine")
                && person.children().size() == 2
                && person.children().stream().anyMatch(child -> child.name().equals("Steve"))
                && person.children().stream().anyMatch(child -> child.name().equals("Hayley"))
        ));
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

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_normal_tool_with_primitive_parameters(ChatModel chatModel) {
        checkSingleToolExecution(chatModel, ReturnBehavior.TO_LLM);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_immediate_tool_with_primitive_parameters(ChatModel chatModel) {
        checkSingleToolExecution(chatModel, ReturnBehavior.IMMEDIATE);
    }

    private void checkSingleToolExecution(ChatModel model, ReturnBehavior returnBehavior) {
        AdderTool toolInstance = null;
        int chatInvocations = 0;

        switch (returnBehavior) {
            case TO_LLM: {
                toolInstance = new ToolWithPrimitiveParameters();
                // 2 times = 2 tool requests + LLM response
                chatInvocations = 2;
                break;
            }
            case IMMEDIATE: {
                toolInstance = new ImmediateToolWithPrimitiveParameters();
                // 1 time, the model is called only once returning immediately the tool result
                chatInvocations = 1;
                break;
            }
        }

        // given
        model = spy(model);

        var tool = spy(toolInstance);

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        var text = adaptPrompt3("How much is 37 plus 87?");

        // when
        var response = assistant.chat(text);

        // then
        if (returnBehavior == ReturnBehavior.TO_LLM) {
            // The tool result is manipulated by the LLM so the response is not equal to the plain tool result
            assertThat(response.content()).contains("124");
            assertThat(response.content()).isNotEqualTo("124");
        } else {
            // The tool result is returned directly so the content response is null
            assertThat(response.content()).isNull();
        }

        assertThat(response.toolExecutions().size()).isEqualTo(1);
        assertThat(response.toolExecutions().get(0).result()).isEqualTo("124");

        verify(tool).add(37, 87);
        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model).supportedCapabilities();
            verify(model, times(chatInvocations)).chat(chatRequestCaptor.capture());

            var toolSpecifications = chatRequestCaptor.getValue().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            var toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("add");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isEqualTo(PRIMITIVE_TOOL_EXPECTED_SCHEMA);
        }
    }

    protected String adaptPrompt3(String prompt) {
        return prompt;
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_normal_tool_in_parallel_with_primitive_parameters(ChatModel chatModel) {
        parallelToolsExecution(chatModel, ReturnBehavior.TO_LLM);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_immediate_tool_in_parallel_with_primitive_parameters(ChatModel chatModel) {
        parallelToolsExecution(chatModel, ReturnBehavior.IMMEDIATE);
    }

    private void parallelToolsExecution(ChatModel model, ReturnBehavior returnBehavior) {
        AdderTool toolInstance = null;
        int chatInvocations = 0;

        switch (returnBehavior) {
            case TO_LLM: {
                toolInstance = new ToolWithPrimitiveParameters();
                // 2 times = 2 tool requests + LLM response
                chatInvocations = 2;
                break;
            }
            case IMMEDIATE: {
                toolInstance = new ImmediateToolWithPrimitiveParameters();
                // 1 time, the model is called only once returning immediately the tool result
                chatInvocations = 1;
                break;
            }
        }

        // given
        model = spy(model);

        var tool = spy(toolInstance);

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        var text = "How much is 37 plus 87? How much is 73 plus 78? Call 2 tools in parallel (at the same time)!";

        // when
        var response = assistant.chat(text);

        // then
        if (returnBehavior == ReturnBehavior.TO_LLM) {
            // The tool is called twice, the result is manipulated by the LLM so the response is not equal to the plain tool result
            assertThat(response.content()).contains("124");
            assertThat(response.content()).contains("151");
        } else {
            // The first tool result is returned immediately so the response is equal to the plain tool result
            assertThat(response.content()).isNull();
            verify(tool).add(37, 87);
        }

        assertThat(response.toolExecutions().size()).isEqualTo(2);
        assertThat(response.toolExecutions().get(0).result()).isEqualTo("124");
        assertThat(response.toolExecutions().get(1).result()).isEqualTo("151");
        verify(tool).add(37, 87);
        verify(tool).add(73, 78);

        verifyNoMoreInteractions(tool);

        if (verifyModelInteractions()) {
            verify(model).supportedCapabilities();
            verify(model, times(chatInvocations)).chat(chatRequestCaptor.capture());

            var toolSpecifications = chatRequestCaptor.getValue().toolSpecifications();
            assertThat(toolSpecifications).hasSize(1);
            var toolSpecification = toolSpecifications.get(0);
            assertThat(toolSpecification.name()).isEqualTo("add");
            assertThat(toolSpecification.description()).isNull();
            assertThat(toolSpecification.parameters()).isEqualTo(PRIMITIVE_TOOL_EXPECTED_SCHEMA);
        }
    }

    static class MultiplyTool {
        @Tool
        int multiply(int a, int b) {
            return a * b;
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_return_to_LLM(ChatModel chatModel) {
        checkMultipleToolsExecution(chatModel, ReturnBehavior.TO_LLM);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_return_immediately_from_first_tool_when_not_called_in_parallel(ChatModel chatModel) {
        checkMultipleToolsExecution(chatModel, ReturnBehavior.IMMEDIATE);
    }

    private void checkMultipleToolsExecution(ChatModel model, ReturnBehavior returnBehavior) {
        AdderTool toolInstance = null;
        int chatInvocations = 0;

        switch (returnBehavior) {
            case TO_LLM: {
                toolInstance = new ToolWithPrimitiveParameters();
                // 3 times = 2 tool requests + LLM response
                chatInvocations = 3;
                break;
            }
            case IMMEDIATE: {
                toolInstance = new ImmediateToolWithPrimitiveParameters();
                // 1 time, the model is called only once returning immediately the tool result
                chatInvocations = 1;
                break;
            }
        }

        // given
        model = spy(model);

        var addTool = spy(toolInstance);
        var multiplyTool = spy(new MultiplyTool());

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(addTool, multiplyTool)
                .build();

        var text = adaptPrompt1("First add 2 and 3, then multiply the result by 4");

        // when
        var response = assistant.chat(text);

        // then
        switch (returnBehavior) {
            case TO_LLM: {
                // the result is not only "20" but is manipulated by the LLM since one of the 2 tools doesn't have direct return
                assertThat(response.content()).contains("20");
                assertThat(response.toolExecutions()).hasSize(2);
                break;
            }
            case IMMEDIATE: {
                // the result is "5" since the first tool has immediate return
                assertThat(response.content()).isNull();
                assertThat(response.toolExecutions()).hasSize(1);
                break;
            }
        }

        // verify tool executions
        assertThat(response.toolExecutions().get(0).result()).isEqualTo("5");
        assertThat(response.toolExecutions().get(0).request().name()).isEqualTo("add");
        verify(addTool).add(2, 3);

        // with immediate return the multiply tool is not executed
        if (returnBehavior != ReturnBehavior.IMMEDIATE) {
            assertThat(response.toolExecutions().get(1).result()).isEqualTo("20");
            assertThat(response.toolExecutions().get(1).request().name()).isEqualTo("multiply");
            verify(multiplyTool).multiply(5, 4);
        }

        verifyNoMoreInteractions(addTool, multiplyTool);

        if (verifyModelInteractions()) {
            verify(model).supportedCapabilities();
            verify(model, times(chatInvocations)).chat(chatRequestCaptor.capture());

            var toolSpecifications = chatRequestCaptor.getValue().toolSpecifications();
            assertThat(toolSpecifications).hasSize(2);
        }

        // perform one more invocation of the AI service to make sure that memory is not corrupted and the service can be used further
        var text2 = "How much is 37 plus 87?";
        var response2 = assistant.chat(text2);
        if (returnBehavior == ReturnBehavior.IMMEDIATE) {
            assertThat(response2.content()).isNull();
        } else {
            assertThat(response2.content()).contains("124");
        }
        assertThat(response2.toolExecutions().get(0).result()).isEqualTo("124");
    }

    protected String adaptPrompt1(String prompt) {
        return prompt;
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_keep_memory_consistent_using_return_immediate(ChatModel model) {
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new ImmediateToolWithPrimitiveParameters())
                .chatMemory(chatMemory)
                .build();

        var response = assistant.chat("How much is 37 plus 87?");
        assertThat(response.content()).isNull();
        assertThat(response.toolExecutions().get(0).result()).isEqualTo("124");

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(3);
        checkMemoryWithImmediateTool(messages, "How much is 37 plus 87?", "124");

        // Check that the memory is not corrupted and conversation can continue
        response = assistant.chat("Now add 47 to the previous result");
        assertThat(response.content()).isNull();
        assertThat(response.toolExecutions().get(0).result()).isEqualTo("171");

        messages = chatMemory.messages();
        assertThat(messages).hasSize(6);
        checkMemoryWithImmediateTool(messages.subList(3, 6), "Now add 47 to the previous result", "171");
    }

    static void checkMemoryWithImmediateTool(List<ChatMessage> messages, String request, String response) {
        // The first message is the user request
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(request);

        // The second message is the AI response with tool execution request
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(1)).toolExecutionRequests()).hasSize(1);
        assertThat(((AiMessage) messages.get(1)).toolExecutionRequests().get(0).name()).isEqualTo("add");

        // The third message is the tool execution result
        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) messages.get(2)).toolName()).isEqualTo("add");
        assertThat(((ToolExecutionResultMessage) messages.get(2)).text()).isEqualTo(response);
    }

    interface StringAssistant {
        String chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_throw_using_immediate_tool_on_service_not_returning_Result(ChatModel model) {
        AdderTool toolInstance = new ImmediateToolWithPrimitiveParameters();

        // given
        model = spy(model);

        var tool = spy(toolInstance);

        var assistant = AiServices.builder(StringAssistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        var text = "How much is 37 plus 87?";

        assertThatThrownBy(() -> assistant.chat(text))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("add");
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_allow_empty_tool_result(ChatModel model) {

        // given
        class Tools {

            @Tool
            String modify(int ignored) {
                return "";
            }
        }

        model = spy(model);

        Tools tools = spy(new Tools());

        StringAssistant assistant = AiServices.builder(StringAssistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        String text = "Call tool 'modify' for argument '7'";

        // when-then
        assertThatNoException().isThrownBy(() -> assistant.chat(text));

        verify(tools).modify(7);

        verify(model).chat(argThat((ChatRequest request) ->
                request.messages().size() == 3
                        && request.messages().get(2).type() == TOOL_EXECUTION_RESULT
                        && ((ToolExecutionResultMessage) request.messages().get(2)).text().isEmpty()
        ));
    }
}
