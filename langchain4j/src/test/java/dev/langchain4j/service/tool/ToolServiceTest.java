package dev.langchain4j.service.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.service.tool.ToolService.shouldReturnImmediately;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ToolServiceTest {

    @Test
    void shouldReturnImmediately_empty_list_should_not_crash() {
        assertThatNoException().isThrownBy(() -> shouldReturnImmediately(false, List.of()));
    }
}