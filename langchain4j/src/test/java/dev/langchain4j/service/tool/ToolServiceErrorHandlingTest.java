package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.invocation.InvocationContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolServiceErrorHandlingTest {

    @Test
    void should_handle_tool_execution_errors_with_lazy_evaluation() {
        // Given
        ErrorTool errorTool = new ErrorTool();
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(errorTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("throwError")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("throwError"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - error is handled properly
        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("Tool execution failed");
    }

    @Test
    void should_handle_tool_arguments_errors_with_lazy_evaluation() {
        // Given
        ErrorTool errorTool = new ErrorTool();
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(errorTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("throwArgumentsError")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("throwArgumentsError"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - arguments error is handled properly
        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("Arguments validation failed");
    }

    @Test
    void should_use_custom_error_handlers_with_lazy_evaluation() {
        // Given
        AtomicInteger customHandlerCallCount = new AtomicInteger(0);
        ErrorTool errorTool = new ErrorTool();
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(errorTool));
        
        // Custom error handlers
        toolService.argumentsErrorHandler((error, context) -> {
            customHandlerCallCount.incrementAndGet();
            return ToolErrorHandlerResult.text("Custom arguments error: " + error.getMessage());
        });
        toolService.executionErrorHandler((error, context) -> {
            customHandlerCallCount.incrementAndGet();
            return ToolErrorHandlerResult.text("Custom execution error: " + error.getMessage());
        });
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("throwError")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("throwError"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - custom error handler is used
        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("Custom execution error: Tool execution failed");
        assertThat(customHandlerCallCount.get()).isEqualTo(1);
    }

    @Test
    void should_maintain_lazy_text_computation_even_with_errors() {
        // Given
        AtomicInteger textComputationCount = new AtomicInteger(0);
        
        // Create a custom tool executor that tracks text computation
        ToolExecutor customExecutor = new ToolExecutor() {
            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultTextSupplier(() -> {
                            textComputationCount.incrementAndGet();
                            return "Error computed lazily";
                        })
                        .build();
            }
            
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "Not used";
            }
        };
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("customError")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        ToolService toolService = new ToolService();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                customExecutor,
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - result is created but text not computed yet
        assertThat(result.isError()).isTrue();
        assertThat(textComputationCount.get()).isEqualTo(0);
        
        // When accessing result text
        String resultText = result.resultText();
        
        // Then - text is computed lazily
        assertThat(resultText).isEqualTo("Error computed lazily");
        assertThat(textComputationCount.get()).isEqualTo(1);
        
        // When accessing again
        String resultText2 = result.resultText();
        
        // Then - text is computed again (no caching)
        assertThat(resultText2).isEqualTo("Error computed lazily");
        assertThat(textComputationCount.get()).isEqualTo(2);
    }

    // Test tool class for error handling testing
    static class ErrorTool {
        
        @Tool("Throws a runtime error")
        public String throwError() {
            throw new RuntimeException("Tool execution failed");
        }
        
        @Tool("Throws a tool arguments error")
        public String throwArgumentsError() {
            throw new ToolArgumentsException(new IllegalArgumentException("Arguments validation failed"));
        }
    }
}