package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
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
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(ListNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

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
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(SetNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

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
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(ResultNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

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
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(ResultListNoParametrizedTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

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
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(ListWildcardTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

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
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(ResultListWildcardTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

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
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(ResultListTypeParamTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'Result<List<MY_TYPE>>' of the method 'ask' must be parameterized with a concrete type, for example: Result<List<String>> or Result<List<MyCustomPojo>>");
    }

    @Test
    void setWildcardTypeInvalidServiceDefinition() {
        interface SetWildcardTypeInvalidServiceDefinition {
            Set<?> ask(String input);
        }
        // Given
        ChatModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AiServices.builder(SetWildcardTypeInvalidServiceDefinition.class)
                        .chatModel(stubModel)
                        .build())
                .actual();

        // Then
        assertThat(illegalArgumentException.getMessage())
                .isEqualTo(
                        "The return type 'Set<?>' of the method 'ask' must be parameterized with a concrete type, for example: Set<String> or Set<MyCustomPojo>");
    }

    @Test
    void getRawClass_shouldThrowNullPointerException_whenTypeIsNull() {
        assertThatThrownBy(() -> TypeUtils.getRawClass(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Type should not be null.");
    }

    @Test
    void getRawClass_shouldHandleArrayType() {
        Type arrayType = String[].class;
        assertThat(TypeUtils.getRawClass(arrayType)).isEqualTo(String[].class);
    }

    @Test
    void typeHasRawClass_shouldReturnFalse_whenTypeIsNull() {
        assertThat(TypeUtils.typeHasRawClass(null, String.class)).isFalse();
    }

    @Test
    void typeHasRawClass_shouldReturnFalse_whenRawClassIsNull() {
        Type type = new TypeReference<String>() {}.getType();
        assertThat(TypeUtils.typeHasRawClass(type, null)).isFalse();
    }

    @Test
    void typeHasRawClass_shouldReturnFalse_whenBothAreNull() {
        assertThat(TypeUtils.typeHasRawClass(null, null)).isFalse();
    }

    @Test
    void typeHasRawClass_shouldReturnFalse_whenClassesDontMatch() {
        Type type = new TypeReference<List<String>>() {}.getType();
        assertThat(TypeUtils.typeHasRawClass(type, Set.class)).isFalse();
    }

    @Test
    void resolveFirstGenericParameterClass_shouldHandleNestedParameterizedTypes() {
        // Given Map<String, List<Integer>>
        Type type = new TypeReference<Map<String, List<Integer>>>() {}.getType();

        // First generic parameter is String
        assertThat(TypeUtils.resolveFirstGenericParameterClass(type)).isEqualTo(String.class);
    }

    @Test
    void resolveFirstGenericParameterType_shouldReturnNull_whenNoTypeArguments() {
        Type type = String.class;
        assertThat(TypeUtils.resolveFirstGenericParameterType(type)).isNull();
    }

    @Test
    void resolveFirstGenericParameterType_shouldReturnFirstType() {
        // Given List<String>
        Type type = new TypeReference<List<String>>() {}.getType();
        Type firstParam = TypeUtils.resolveFirstGenericParameterType(type);

        assertThat(firstParam).isEqualTo(String.class);
    }

    @Test
    void resolveFirstGenericParameterType_shouldReturnParameterizedType() {
        // Given Result<List<String>>
        Type type = new TypeReference<Result<List<String>>>() {}.getType();
        Type firstParam = TypeUtils.resolveFirstGenericParameterType(type);

        assertThat(TypeUtils.getRawClass(firstParam)).isEqualTo(List.class);
    }

    @Test
    void resolveFirstGenericParameterType_shouldThrowException_whenTypeIsNull() {
        assertThatThrownBy(() -> TypeUtils.resolveFirstGenericParameterType(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveFirstGenericParameterClass_withMultipleTypeParams() {
        // Given Map<String, Integer>
        Type type = new TypeReference<Map<String, Integer>>() {}.getType();

        // Should return only the first parameter class
        assertThat(TypeUtils.resolveFirstGenericParameterClass(type)).isEqualTo(String.class);
    }

    @Test
    void resolveFirstGenericParameterType_withMultipleTypeParams() {
        // Given Map<String, Integer>
        Type type = new TypeReference<Map<String, Integer>>() {}.getType();

        // Should return only the first parameter type
        Type firstParam = TypeUtils.resolveFirstGenericParameterType(type);
        assertThat(firstParam).isEqualTo(String.class);
    }
}
