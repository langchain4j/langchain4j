package dev.langchain4j.service;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TypeUtilsTest {

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

}