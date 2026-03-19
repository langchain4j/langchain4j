package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verify that the AIServices builder doesn't allow setting more than one of
 * (retriever, contentRetriever, retrievalAugmentor).
 */
class AiServicesBuilderTest {

    interface TestService {
        String chat(String userMessage);
    }

    @Test
    void contentRetrieverAndRetrievalAugmentor() {
        ContentRetriever contentRetriever = mock(ContentRetriever.class);
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(TestService.class)
                    .contentRetriever(contentRetriever)
                    .retrievalAugmentor(retrievalAugmentor)
                    .build();
        });
    }

    @Test
    void retrievalAugmentorAndContentRetriever() {
        ContentRetriever contentRetriever = mock(ContentRetriever.class);
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(TestService.class)
                    .retrievalAugmentor(retrievalAugmentor)
                    .contentRetriever(contentRetriever)
                    .build();
        });
    }

    @Test
    void should_raise_an_error_when_tools_are_classes() {
        class HelloWorld {
            @Tool("Say hello")
            void add(String name) {
                System.out.printf("Hello %s!", name);
            }
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(HelloWorld.class)
                        .build());
    }

    @Test
    void should_throw_when_chat_model_is_null() {
        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() ->
                        AiServices.builder(TestService.class).chatModel(null).build())
                .withMessageContaining("chatModel");
    }

    @Test
    void should_throw_when_multiple_retrievers_set() {
        ContentRetriever contentRetriever1 = mock(ContentRetriever.class);
        ContentRetriever contentRetriever2 = mock(ContentRetriever.class);

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .contentRetriever(contentRetriever1)
                        .contentRetriever(contentRetriever2)
                        .build());
    }

    @Test
    void should_allow_building_with_only_chat_model() {
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("response");

        TestService service =
                AiServices.builder(TestService.class).chatModel(chatModel).build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_raise_an_error_when_object_has_no_tool_methods() {
        class ObjectWithoutTools {
            public void doSomething() {
                // no @Tool annotation
            }
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ObjectWithoutTools())
                        .build())
                .withMessageContaining("does not have any methods annotated with @Tool");
    }

    @Test
    void should_raise_an_error_when_nested_collection_is_passed_as_tool() {
        class ToolClass {
            @Tool("Say hello")
            void sayHello(String name) {
                System.out.printf("Hello %s!", name);
            }
        }

        // This simulates the case where someone accidentally wraps tools in a nested collection
        // e.g., tools(Arrays.asList(toolsList)) instead of tools(toolsList)
        List<Object> innerTools = Arrays.asList(new ToolClass());
        List<Object> outerCollection = Arrays.asList(innerTools);

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(outerCollection) // passing a nested collection
                        .build())
                .withMessageContaining("is an Iterable");
    }

    @Test
    void should_raise_an_error_when_list_is_passed_as_tool_element() {
        class ToolClass {
            @Tool("Say hello")
            void sayHello(String name) {
                System.out.printf("Hello %s!", name);
            }
        }

        // The varargs version: tools(listOfTools) where listOfTools is itself a list
        List<Object> listOfTools = Arrays.asList(new ToolClass());

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools((Object) listOfTools) // passing a List as a single tool object
                        .build())
                .withMessageContaining("is an Iterable");
    }

    // --- includeInheritedFields builder tests ---

    static class BaseToolParam {
        String baseField;
    }

    static class DerivedToolParam extends BaseToolParam {
        String derivedField;
    }

    static class InheritedFieldTool {
        @Tool("do work")
        public void doWork(@P("param") DerivedToolParam param) {}
    }

    @Test
    void should_include_inherited_fields_in_tool_schema_when_flag_set() {
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("response");

        // tools() called BEFORE includeInheritedFields() — deferred materialization ensures it works
        AiServices<TestService> builder = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .tools(new InheritedFieldTool())
                .includeInheritedFields(true);

        TestService service = builder.build();
        assertThat(service).isNotNull();

        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new InheritedFieldTool(), true);
        assertThat(specs).hasSize(1);
        JsonObjectSchema paramSchema =
                (JsonObjectSchema) specs.get(0).parameters().properties().get("arg0");
        assertThat(paramSchema.properties()).containsKey("derivedField");
        assertThat(paramSchema.properties()).containsKey("baseField");
    }

    @Test
    void should_not_include_inherited_fields_by_default() {
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("response");

        AiServices<TestService> builder =
                AiServices.builder(TestService.class).chatModel(chatModel).tools(new InheritedFieldTool());

        TestService service = builder.build();
        assertThat(service).isNotNull();

        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new InheritedFieldTool());
        assertThat(specs).hasSize(1);
        JsonObjectSchema paramSchema =
                (JsonObjectSchema) specs.get(0).parameters().properties().get("arg0");
        assertThat(paramSchema.properties()).containsKey("derivedField");
        assertThat(paramSchema.properties()).doesNotContainKey("baseField");
    }

    @Test
    void should_include_inherited_fields_regardless_of_builder_order() {
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("response");

        // includeInheritedFields() called BEFORE tools()
        AiServices<TestService> builder = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .includeInheritedFields(true)
                .tools(new InheritedFieldTool());

        TestService service = builder.build();
        assertThat(service).isNotNull();

        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new InheritedFieldTool(), true);
        assertThat(specs).hasSize(1);
        JsonObjectSchema paramSchema =
                (JsonObjectSchema) specs.get(0).parameters().properties().get("arg0");
        assertThat(paramSchema.properties()).containsKey("derivedField");
        assertThat(paramSchema.properties()).containsKey("baseField");
    }
}
