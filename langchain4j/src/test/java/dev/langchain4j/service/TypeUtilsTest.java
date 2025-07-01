package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeUtilsTest {

    /**********************************************************************************************
     * Tests covering:
     * dev.langchain4j.service.TypeUtils#getRawClass(java.lang.reflect.Type)
     * dev.langchain4j.service.TypeUtils#typeHasRawClass(java.lang.reflect.Type, java.lang.Class)
     * dev.langchain4j.service.TypeUtils#resolveFirstGenericParameterClass(java.lang.reflect.Type)
     **********************************************************************************************/
    @Test
    void integerReturnType() {
        // Given Integer
        Type returnType = new TypeReference<Integer>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(Integer.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, Integer.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isNull();
    }

    @Test
    void stringReturnType() {
        // Given String
        Type returnType = new TypeReference<String>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(String.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, String.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isNull();
    }

    @Test
    void resultStringReturnType() {
        // Given Result<String>
        Type returnType = new TypeReference<Result<String>>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(Result.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, Result.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isEqualTo(String.class);
    }

    @Test
    void listOfStringsReturnType() {
        // Given List<String>
        Type returnType = new TypeReference<List<String>>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(List.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, List.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isEqualTo(String.class);
    }

    @Test
    void setOfIntegersReturnType() {
        // Given Set<Integer>
        Type returnType = new TypeReference<Set<Integer>>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(Set.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, Set.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isEqualTo(Integer.class);
    }

    @Test
    void resultSetOfIntegersReturnType() {
        // Given Result<Set<Integer>
        Type returnType = new TypeReference<Result<Set<Integer>>>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(Result.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, Result.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isEqualTo(Set.class);
    }

    /**********************************************************************************************
     * Tests covering:
     * dev.langchain4j.service.TypeUtils#validateReturnTypesAreProperlyParametrized(java.lang.String, java.lang.reflect.Type)
     **********************************************************************************************/
    interface ListNoParametrizedTypeInvalidServiceDefinition {
        List ask(String input);
    }

    @Test
    void listNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class, () -> AiServices.builder(
                                ListNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build());

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'List' of the method 'ask' must be parameterized with a concrete type, for example: List<String> or List<MyCustomPojo>");
    }

    interface SetNoParametrizedTypeInvalidServiceDefinition {
        Set ask(String input);
    }

    @Test
    void setNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class, () -> AiServices.builder(
                                SetNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build());

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'Set' of the method 'ask' must be parameterized with a concrete type, for example: Set<String> or Set<MyCustomPojo>");
    }

    interface ResultNoParametrizedTypeInvalidServiceDefinition {
        Result ask(String input);
    }

    @Test
    void resultNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class, () -> AiServices.builder(
                                ResultNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build());

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'Result' of the method 'ask' must be parameterized with a concrete type, for example: Result<String> or Result<MyCustomPojo>");
    }

    interface ResultListNoParametrizedTypeInvalidServiceDefinition {
        Result<List> ask(String input);
    }

    @Test
    void resultListNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class, () -> AiServices.builder(
                                ResultListNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build());

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'Result<List>' of the method 'ask' must be parameterized with a concrete type, for example: Result<List<String>> or Result<List<MyCustomPojo>>");
    }

    interface ListWildcardTypeInvalidServiceDefinition {
        List<?> ask(String input);
    }

    @Test
    void listWildcardTypeInvalidServiceDefinition() {
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(
                IllegalArgumentException.class, () -> AiServices.builder(ListWildcardTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build());

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'List<?>' of the method 'ask' must be parameterized with a concrete type, for example: List<String> or List<MyCustomPojo>");
    }

    interface ResultListWildcardTypeInvalidServiceDefinition {
        Result<List<?>> ask(String input);
    }

    @Test
    void resultListWildcardTypeInvalidServiceDefinition() {
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class, () -> AiServices.builder(
                                ResultListWildcardTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build());

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'Result<List<?>>' of the method 'ask' must be parameterized with a concrete type, for example: Result<List<String>> or Result<List<MyCustomPojo>>");
    }

    interface ResultListTypeParamTypeInvalidServiceDefinition<MY_TYPE extends Object> {
        Result<List<MY_TYPE>> ask(String input);
    }

    @Test
    void resultListTypeParamTypeInvalidServiceDefinition() {
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class, () -> AiServices.builder(
                                ResultListTypeParamTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build());

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'Result<List<MY_TYPE>>' of the method 'ask' must be parameterized with a concrete type, for example: Result<List<String>> or Result<List<MyCustomPojo>>");
    }
}
