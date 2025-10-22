package dev.langchain4j.internal;

import dev.langchain4j.exception.LangChain4jException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for JsonSerializationErrorHandler.
 */
class JsonSerializationErrorHandlerTest {

    @Nested
    @DisplayName("Supplier Error Handling")
    class SupplierErrorHandlingTests {

        @Test
        @DisplayName("Should handle supplier error with null fallback")
        void shouldHandleSupplierErrorWithNullFallback() {
            RuntimeException supplierException = new RuntimeException("Supplier failed");
            
            String result = JsonSerializationErrorHandler.handleSupplierError(supplierException, null);
            
            assertThat(result).contains("LazyEvaluation Error")
                             .contains("RuntimeException")
                             .contains("Supplier failed");
        }

        @Test
        @DisplayName("Should handle supplier error with successful fallback")
        void shouldHandleSupplierErrorWithSuccessfulFallback() {
            RuntimeException supplierException = new RuntimeException("Primary supplier failed");
            Supplier<Object> fallbackSupplier = () -> "fallback value";
            
            String result = JsonSerializationErrorHandler.handleSupplierError(supplierException, fallbackSupplier);
            
            assertThat(result).isEqualTo("fallback value");
        }

        @Test
        @DisplayName("Should handle supplier error with failing fallback")
        void shouldHandleSupplierErrorWithFailingFallback() {
            RuntimeException supplierException = new RuntimeException("Primary failed");
            Supplier<Object> fallbackSupplier = () -> {
                throw new IllegalStateException("Fallback also failed");
            };
            
            String result = JsonSerializationErrorHandler.handleSupplierError(supplierException, fallbackSupplier);
            
            assertThat(result).contains("LazyEvaluation Multiple Errors")
                             .contains("Primary[RuntimeException: Primary failed]")
                             .contains("Fallback[IllegalStateException: Fallback also failed]");
        }

        @Test
        @DisplayName("Should handle supplier error with fallback returning null")
        void shouldHandleSupplierErrorWithFallbackReturningNull() {
            RuntimeException supplierException = new RuntimeException("Primary failed");
            Supplier<Object> fallbackSupplier = () -> null;
            
            String result = JsonSerializationErrorHandler.handleSupplierError(supplierException, fallbackSupplier);
            
            assertThat(result).isEqualTo("null");
        }
    }

    @Nested
    @DisplayName("JSON Serialization Error Handling")
    class JsonSerializationErrorHandlingTests {

        @Test
        @DisplayName("Should handle JSON serialization error with successful toString fallback")
        void shouldHandleJsonSerializationErrorWithSuccessfulToString() {
            RuntimeException jsonException = new RuntimeException("JSON serialization failed");
            Object originalValue = "test value";
            
            String result = JsonSerializationErrorHandler.handleJsonSerializationError(jsonException, originalValue);
            
            assertThat(result).isEqualTo("test value");
        }

        @Test
        @DisplayName("Should handle JSON serialization error with null value")
        void shouldHandleJsonSerializationErrorWithNullValue() {
            RuntimeException jsonException = new RuntimeException("JSON serialization failed");
            
            String result = JsonSerializationErrorHandler.handleJsonSerializationError(jsonException, null);
            
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("Should handle JSON serialization error with toString failure")
        void shouldHandleJsonSerializationErrorWithToStringFailure() {
            RuntimeException jsonException = new RuntimeException("JSON serialization failed");
            Object problematicObject = new Object() {
                @Override
                public String toString() {
                    throw new RuntimeException("toString also failed");
                }
            };
            
            String result = JsonSerializationErrorHandler.handleJsonSerializationError(jsonException, problematicObject);
            
            assertThat(result).contains("LazyEvaluation Error")
                             .contains("RuntimeException")
                             .contains("toString also failed");
        }
    }

    @Nested
    @DisplayName("ToString Fallback Handling")
    class ToStringFallbackHandlingTests {

        @Test
        @DisplayName("Should handle toString fallback with null value")
        void shouldHandleToStringFallbackWithNull() {
            String result = JsonSerializationErrorHandler.handleToStringFallback(null);
            
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("Should handle toString fallback with successful toString")
        void shouldHandleToStringFallbackWithSuccessfulToString() {
            Object value = "test value";
            
            String result = JsonSerializationErrorHandler.handleToStringFallback(value);
            
            assertThat(result).isEqualTo("test value");
        }

        @Test
        @DisplayName("Should handle toString fallback with toString failure")
        void shouldHandleToStringFallbackWithToStringFailure() {
            Object problematicObject = new Object() {
                @Override
                public String toString() {
                    throw new RuntimeException("toString failed");
                }
            };
            
            String result = JsonSerializationErrorHandler.handleToStringFallback(problematicObject);
            
            assertThat(result).contains("LazyEvaluation Error")
                             .contains("RuntimeException")
                             .contains("toString failed");
        }
    }

    @Nested
    @DisplayName("Error Message Creation")
    class ErrorMessageCreationTests {

        @Test
        @DisplayName("Should create error message for single exception")
        void shouldCreateErrorMessageForSingleException() {
            RuntimeException exception = new RuntimeException("Test error message");
            
            String result = JsonSerializationErrorHandler.createErrorMessage(exception);
            
            assertThat(result).isEqualTo("LazyEvaluation Error: RuntimeException - Test error message");
        }

        @Test
        @DisplayName("Should create error message for exception with null message")
        void shouldCreateErrorMessageForExceptionWithNullMessage() {
            RuntimeException exception = new RuntimeException((String) null);
            
            String result = JsonSerializationErrorHandler.createErrorMessage(exception);
            
            assertThat(result).isEqualTo("LazyEvaluation Error: RuntimeException - Unknown error");
        }

        @Test
        @DisplayName("Should create error message for multiple exceptions")
        void shouldCreateErrorMessageForMultipleExceptions() {
            RuntimeException primaryException = new RuntimeException("Primary error");
            IllegalStateException fallbackException = new IllegalStateException("Fallback error");
            
            String result = JsonSerializationErrorHandler.createErrorMessage(primaryException, fallbackException);
            
            assertThat(result).isEqualTo("LazyEvaluation Multiple Errors: Primary[RuntimeException: Primary error], Fallback[IllegalStateException: Fallback error]");
        }

        @Test
        @DisplayName("Should create error message for multiple exceptions with null messages")
        void shouldCreateErrorMessageForMultipleExceptionsWithNullMessages() {
            RuntimeException primaryException = new RuntimeException((String) null);
            IllegalStateException fallbackException = new IllegalStateException((String) null);
            
            String result = JsonSerializationErrorHandler.createErrorMessage(primaryException, fallbackException);
            
            assertThat(result).isEqualTo("LazyEvaluation Multiple Errors: Primary[RuntimeException: Unknown error], Fallback[IllegalStateException: Unknown error]");
        }
    }

    @Nested
    @DisplayName("Critical Error Creation")
    class CriticalErrorCreationTests {

        @Test
        @DisplayName("Should create critical error with message and cause")
        void shouldCreateCriticalErrorWithMessageAndCause() {
            String message = "Critical error occurred";
            RuntimeException cause = new RuntimeException("Root cause");
            
            LangChain4jException result = JsonSerializationErrorHandler.createCriticalError(message, cause);
            
            assertThat(result).hasMessage(message)
                             .hasCause(cause);
        }

        @Test
        @DisplayName("Should create critical error with null cause")
        void shouldCreateCriticalErrorWithNullCause() {
            String message = "Critical error occurred";
            
            LangChain4jException result = JsonSerializationErrorHandler.createCriticalError(message, null);
            
            assertThat(result).hasMessage(message)
                             .hasNoCause();
        }
    }

    @Nested
    @DisplayName("Supplier Validation")
    class SupplierValidationTests {

        @Test
        @DisplayName("Should validate non-null supplier successfully")
        void shouldValidateNonNullSupplierSuccessfully() {
            Supplier<Object> supplier = () -> "test";
            
            assertThatCode(() -> JsonSerializationErrorHandler.validateSupplier(supplier, "testSupplier"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw exception for null supplier")
        void shouldThrowExceptionForNullSupplier() {
            assertThatThrownBy(() -> JsonSerializationErrorHandler.validateSupplier(null, "testSupplier"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Supplier parameter 'testSupplier' cannot be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"supplier1", "valueSupplier", "fallbackSupplier"})
        @DisplayName("Should include parameter name in validation error message")
        void shouldIncludeParameterNameInValidationErrorMessage(String parameterName) {
            assertThatThrownBy(() -> JsonSerializationErrorHandler.validateSupplier(null, parameterName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Supplier parameter '" + parameterName + "' cannot be null");
        }
    }
}