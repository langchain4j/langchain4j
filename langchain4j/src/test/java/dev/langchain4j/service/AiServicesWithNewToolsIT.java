package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.output.Response;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;

import static dev.langchain4j.model.chat.request.json.JsonBooleanSchema.JSON_BOOLEAN_SCHEMA;
import static dev.langchain4j.model.chat.request.json.JsonIntegerSchema.JSON_INTEGER_SCHEMA;
import static dev.langchain4j.model.chat.request.json.JsonNumberSchema.JSON_NUMBER_SCHEMA;
import static dev.langchain4j.model.chat.request.json.JsonStringSchema.JSON_STRING_SCHEMA;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public abstract class AiServicesWithNewToolsIT {

    @Captor
    private ArgumentCaptor<List<ToolSpecification>> toolSpecificationCaptor;

    protected abstract List<ChatLanguageModel> models();

    interface Assistant {

        Response<AiMessage> chat(String userMessage);
    }

    // TODO cover all cases similar to AiServicesJsonSchemaIT and AiServicesJsonSchemaWithDescriptionsIT
    // TODO no arguments
    // TODO single argument: primitives, enum, pojo with primitives, pojo with pojos, map?
    // TODO single argument: List/Set/Array of primitives, List/Set/Array of enums, List/Set/Array of POJOs, map?
    // TODO multiple arguments
    // TODO with descriptions, including @Description

    static class Tools1 { // TODO name

        @AllArgsConstructor
        @EqualsAndHashCode
        static class Person {

            String name;
            int age;
            Double height;
            boolean married;
        }

        @Tool
        void process(Person person) {
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .properties(singletonMap("arg0", JsonObjectSchema.builder()
                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                            put("name", JSON_STRING_SCHEMA);
                            put("age", JSON_INTEGER_SCHEMA);
                            put("height", JSON_NUMBER_SCHEMA);
                            put("married", JSON_BOOLEAN_SCHEMA);
                        }})
                        .required("name", "age", "height", "married")
                        .additionalProperties(false)
                        .build()))
                .required("arg0")
                .build();
    }

    @Test
    void should_execute_tool_with_pojo_with_primitives() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            Tools1 tools = spy(new Tools1());

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(model)
                    .tools(tools)
                    .build();

            String text = "Use 'process' tool to process the following: Klaus is 37 years old, 1.78m height and single";

            // when
            assistant.chat(text);

            // then
            verify(tools).process(new Tools1.Person("Klaus", 37, 1.78, false));
            verifyNoMoreInteractions(tools);

            if (verifyModelInteractions()) {
                verify(model).supportedCapabilities();
                verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
                verifyNoMoreInteractions(model);

                List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
                assertThat(toolSpecifications).hasSize(1);
                ToolSpecification toolSpecification = toolSpecifications.get(0);
                assertThat(toolSpecification.name()).isEqualTo("process");
                assertThat(toolSpecification.description()).isNull();
                assertThat(toolSpecification.parameters()).isEqualTo(Tools1.EXPECTED_SCHEMA);
            }
        }
    }

    static class Tools2 { // TODO name

        @AllArgsConstructor
        @EqualsAndHashCode
        static class Person {

            String name;
            Address address;
        }

        @AllArgsConstructor
        @EqualsAndHashCode
        static class Address {

            String city;
        }

        @Tool
        void process(Person person) {
        }

        static JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                .properties(singletonMap("arg0", JsonObjectSchema.builder()
                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                            put("name", JSON_STRING_SCHEMA);
                            put("address", JsonObjectSchema.builder()
                                    .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                        put("city", JSON_STRING_SCHEMA);
                                    }})
                                    .required("city")
                                    .additionalProperties(false)
                                    .build());
                        }})
                        .required("name", "address")
                        .additionalProperties(false)
                        .build()))
                .required("arg0")
                .build();
    }

    @Test
    void should_execute_tool_with_pojo_with_nested_pojo() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            Tools2 tools = spy(new Tools2());

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(model)
                    .tools(tools)
                    .build();

            String text = "Use 'process' tool to process the following: Klaus Heissler lives in Langley Falls";

            // when
            assistant.chat(text);

            // then
            verify(tools).process(new Tools2.Person("Klaus Heissler", new Tools2.Address("Langley Falls")));
            verifyNoMoreInteractions(tools);

            if (verifyModelInteractions()) {
                verify(model).supportedCapabilities();
                verify(model, times(2)).generate(anyList(), toolSpecificationCaptor.capture());
                verifyNoMoreInteractions(model);

                List<ToolSpecification> toolSpecifications = toolSpecificationCaptor.getValue();
                assertThat(toolSpecifications).hasSize(1);
                ToolSpecification toolSpecification = toolSpecifications.get(0);
                assertThat(toolSpecification.name()).isEqualTo("process");
                assertThat(toolSpecification.description()).isNull();
                assertThat(toolSpecification.parameters()).isEqualTo(Tools2.EXPECTED_SCHEMA);
            }
        }
    }

    protected boolean verifyModelInteractions() {
        return true;
    }
}
