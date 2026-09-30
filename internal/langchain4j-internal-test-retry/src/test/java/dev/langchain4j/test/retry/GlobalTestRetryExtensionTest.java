package dev.langchain4j.test.retry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The extension is registered explicitly, so that these tests do not depend on the auto-detection of extensions,
 * which is disabled by default (e.g., when running the tests from an IDE).
 * It is still active only when the "LC4J_GLOBAL_TEST_RETRY_ENABLED" environment variable is set to "true",
 * see the surefire configuration in pom.xml.
 */
@EnabledIfEnvironmentVariable(named = "LC4J_GLOBAL_TEST_RETRY_ENABLED", matches = "true")
@ExtendWith(GlobalTestRetryExtension.class)
class GlobalTestRetryExtensionTest extends AbstractRetryTestBase {

    private int attempts;

    @BeforeEach
    void beforeEach() {
        events.add("beforeEach");
    }

    @AfterEach
    void afterEach() {
        events.add("afterEach");
    }

    @Test
    void should_re_run_lifecycle_methods_before_retrying() {

        events.add("test");

        if (attempts++ == 0) {
            throw new AssertionError("failing the first attempt on purpose to trigger a retry");
        }

        assertEquals(
                List.of(
                        "baseBeforeEach", "beforeEach", "test", // first attempt
                        "afterEach", "baseAfterEach", "baseBeforeEach", "beforeEach", // state reset
                        "test"), // second attempt
                events);
    }

    @Test
    void should_not_re_run_lifecycle_methods_when_test_does_not_fail() {

        events.add("test");

        assertEquals(List.of("baseBeforeEach", "beforeEach", "test"), events);
    }
}
