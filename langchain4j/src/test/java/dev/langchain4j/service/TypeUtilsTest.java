package dev.langchain4j.service;

import com.google.gson.reflect.TypeToken;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeUtilsTest {

    /**********************************************************************************************
     * Tests covering:
     * dev.langchain4j.service.TypeUtils#getRawClass(java.lang.reflect.Type)
     * dev.langchain4j.service.TypeUtils#typeHasRawClass(java.lang.reflect.Type, java.lang.Class)
     * dev.langchain4j.service.TypeUtils#resolveFirstGenericParameterClass(java.lang.reflect.Type)
     **********************************************************************************************/

    @Test
    public void testIntegerReturnType() {
        // Given Integer
        Type returnType = new TypeToken<Integer>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(Integer.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, Integer.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isNull();
    }

    @Test
    public void testStringReturnType() {
        // Given String
        Type returnType = new TypeToken<String>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(String.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, String.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isNull();
    }

    @Test
    public void testResultStringReturnType() {
        // Given Result<String>
        Type returnType = new TypeToken<Result<String>>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(Result.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, Result.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isEqualTo(String.class);
    }

    @Test
    public void testListOfStringsReturnType() {
        // Given List<String>
        Type returnType = new TypeToken<List<String>>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(List.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, List.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isEqualTo(String.class);
    }

    @Test
    public void testSetOfIntegersReturnType() {
        // Given Set<Integer>
        Type returnType = new TypeToken<Set<Integer>>() {}.getType();

        // Then
        assertThat(TypeUtils.getRawClass(returnType)).isEqualTo(Set.class);
        assertThat(TypeUtils.typeHasRawClass(returnType, Set.class)).isTrue();
        assertThat(TypeUtils.resolveFirstGenericParameterClass(returnType)).isEqualTo(Integer.class);
    }

    @Test
    public void testResultSetOfIntegersReturnType() {
        // Given Result<Set<Integer>
        Type returnType = new TypeToken<Result<Set<Integer>>>() {}.getType();

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
    public void testListNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatLanguageModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            AiServices.builder(ListNoParametrizedTypeInvalidServiceDefinition.class)
                    .chatLanguageModel(stubModel)
                    .build();
        });

        // Then
        AssertionsForClassTypes.assertThat(illegalArgumentException.getMessage()).isEqualTo("The return type 'List' of the method 'ask' must be parameterized with a concrete type, for example: List<String> or List<MyCustomPojo>");
    }


    interface SetNoParametrizedTypeInvalidServiceDefinition {
        Set ask(String input);
    }

    @Test
    public void testSetNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatLanguageModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            AiServices.builder(SetNoParametrizedTypeInvalidServiceDefinition.class)
                    .chatLanguageModel(stubModel)
                    .build();
        });

        // Then
        AssertionsForClassTypes.assertThat(illegalArgumentException.getMessage()).isEqualTo("The return type 'Set' of the method 'ask' must be parameterized with a concrete type, for example: Set<String> or Set<MyCustomPojo>");
    }

    interface ResultNoParametrizedTypeInvalidServiceDefinition {
        Result ask(String input);
    }

    @Test
    public void testResultNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatLanguageModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            AiServices.builder(ResultNoParametrizedTypeInvalidServiceDefinition.class)
                    .chatLanguageModel(stubModel)
                    .build();
        });

        // Then
        AssertionsForClassTypes.assertThat(illegalArgumentException.getMessage()).isEqualTo("The return type 'Result' of the method 'ask' must be parameterized with a concrete type, for example: Result<String> or Result<MyCustomPojo>");
    }

    interface ResultListNoParametrizedTypeInvalidServiceDefinition {
        Result<List> ask(String input);
    }

    @Test
    public void testResultListNoParametrizedTypeInvalidServiceDefinition() {
        // Given
        ChatLanguageModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            AiServices.builder(ResultListNoParametrizedTypeInvalidServiceDefinition.class)
                    .chatLanguageModel(stubModel)
                    .build();
        });

        // Then
        AssertionsForClassTypes.assertThat(illegalArgumentException.getMessage()).isEqualTo("The return type 'Result<List>' of the method 'ask' must be parameterized with a concrete type, for example: Result<List<String>> or Result<List<MyCustomPojo>>");
    }

    interface ListWildcardTypeInvalidServiceDefinition {
        List<?> ask(String input);
    }

    @Test
    public void testListWildcardTypeInvalidServiceDefinition() {
        // Given
        ChatLanguageModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            AiServices.builder(ListWildcardTypeInvalidServiceDefinition.class)
                    .chatLanguageModel(stubModel)
                    .build();
        });

        // Then
        AssertionsForClassTypes.assertThat(illegalArgumentException.getMessage()).isEqualTo("The return type 'List<?>' of the method 'ask' must be parameterized with a concrete type, for example: List<String> or List<MyCustomPojo>");
    }

    interface ResultListWildcardTypeInvalidServiceDefinition {
        Result<List<?>> ask(String input);
    }

    @Test
    public void testResultListWildcardTypeInvalidServiceDefinition() {
        // Given
        ChatLanguageModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            AiServices.builder(ResultListWildcardTypeInvalidServiceDefinition.class)
                    .chatLanguageModel(stubModel)
                    .build();
        });

        // Then
        AssertionsForClassTypes.assertThat(illegalArgumentException.getMessage()).isEqualTo("The return type 'Result<List<?>>' of the method 'ask' must be parameterized with a concrete type, for example: Result<List<String>> or Result<List<MyCustomPojo>>");
    }

    interface ResultListTypeParamTypeInvalidServiceDefinition<MY_TYPE extends Object> {
        Result<List<MY_TYPE>> ask(String input);
    }

    @Test
    public void testResultListTypeParamTypeInvalidServiceDefinition() {
        // Given
        ChatLanguageModel stubModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        // When
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            AiServices.builder(ResultListTypeParamTypeInvalidServiceDefinition.class)
                    .chatLanguageModel(stubModel)
                    .build();
        });

        // Then
        AssertionsForClassTypes.assertThat(illegalArgumentException.getMessage()).isEqualTo("The return type 'Result<List<MY_TYPE>>' of the method 'ask' must be parameterized with a concrete type, for example: Result<List<String>> or Result<List<MyCustomPojo>>");
    }

}