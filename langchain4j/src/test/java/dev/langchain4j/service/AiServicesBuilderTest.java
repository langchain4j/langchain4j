package dev.langchain4j.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
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

    @Test
    void should_raise_an_error_when_primitive_tool_parameter_is_marked_as_optional() {
        class ToolWithOptionalPrimitive {
            @Tool("Read part of a file")
            void readFile(String filePath, @P(required = false) int startLine) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ToolWithOptionalPrimitive())
                        .build())
                .withMessageContaining("is a primitive")
                .withMessageContaining("@P(required = false)");
    }

    @Test
    void should_allow_optional_primitive_when_default_value_is_set() {
        class ToolWithDefaultPrimitive {
            @Tool("Read part of a file")
            void readFile(String filePath, @P(required = false, defaultValue = "0") int startLine) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        TestService service = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .tools(new ToolWithDefaultPrimitive())
                .build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_raise_an_error_when_default_value_cannot_be_parsed() {
        class ToolWithBadDefault {
            @Tool
            void tool(@P(defaultValue = "not-an-int") int x) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ToolWithBadDefault())
                        .build())
                .withMessageContaining("Cannot parse @P(defaultValue = \"not-an-int\")");
    }

    @Test
    void should_raise_an_error_when_default_value_is_combined_with_Optional() {
        class ToolWithDefaultAndOptional {
            @Tool
            void tool(@P(defaultValue = "10") java.util.Optional<Integer> x) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ToolWithDefaultAndOptional())
                        .build())
                .withMessageContaining("Optional<T>");
    }

    @Test
    void should_raise_an_error_when_default_value_overflows_target_type() {
        // 999999999999999 fits in long but not int → bounds check at registration time.
        class ToolWithOverflowingDefault {
            @Tool
            void tool(@P(defaultValue = "999999999999999") int x) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ToolWithOverflowingDefault())
                        .build())
                .withMessageContaining("Cannot parse @P(defaultValue = \"999999999999999\")");
    }

    @Test
    void should_raise_an_error_when_default_value_is_not_a_valid_boolean() {
        class ToolWithBadBooleanDefault {
            @Tool
            void tool(@P(defaultValue = "yes") boolean x) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ToolWithBadBooleanDefault())
                        .build())
                .withMessageContaining("Cannot parse @P(defaultValue = \"yes\")");
    }

    enum Currency {
        USD,
        EUR
    }

    @Test
    void should_raise_an_error_when_default_value_is_not_a_valid_enum_constant() {
        class ToolWithBadEnumDefault {
            @Tool
            void tool(@P(defaultValue = "ZZZ") Currency c) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ToolWithBadEnumDefault())
                        .build())
                .withMessageContaining("Cannot parse @P(defaultValue = \"ZZZ\")");
    }

    @Test
    void should_raise_an_error_when_default_value_is_not_a_valid_UUID() {
        class ToolWithBadUuidDefault {
            @Tool
            void tool(@P(defaultValue = "not-a-uuid") java.util.UUID id) {}
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(new ToolWithBadUuidDefault())
                        .build())
                .withMessageContaining("Cannot parse @P(defaultValue = \"not-a-uuid\")");
    }
}
