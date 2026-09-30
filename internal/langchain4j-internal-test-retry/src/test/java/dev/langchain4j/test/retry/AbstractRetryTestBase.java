package dev.langchain4j.test.retry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractRetryTestBase {

    final List<String> events = new ArrayList<>();

    @BeforeEach
    void baseBeforeEach() {
        events.add("baseBeforeEach");
    }

    @AfterEach
    void baseAfterEach() {
        events.add("baseAfterEach");
    }
}
