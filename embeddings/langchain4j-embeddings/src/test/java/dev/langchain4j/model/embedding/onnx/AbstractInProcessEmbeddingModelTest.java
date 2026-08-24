package dev.langchain4j.model.embedding.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class AbstractInProcessEmbeddingModelTest {

    // Verifies that model resources do not depend on the thread context class loader.
    @Test
    void should_load_resource_when_context_class_loader_cannot_find_it() throws IOException {
        // Preserve the original thread context class loader.
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        // Obtain the class loader that defines the model class.
        ClassLoader modelClassLoader = AbstractInProcessEmbeddingModelTest.class.getClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {});

            // Read a resource that is visible only to the model class loader.
            try (InputStream resource =
                    AbstractInProcessEmbeddingModel.getResourceAsStream(modelClassLoader, "bert-tokenizer.json")) {
                assertThat(resource).isNotNull();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    // Verifies that resource loading falls back when the model class loader cannot find the resource.
    @Test
    void should_fall_back_to_context_class_loader() throws IOException {
        // Preserve the original thread context class loader.
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        // Create a model class loader that cannot find the fallback resource.
        ClassLoader modelClassLoader = new ClassLoader(null) {};

        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
                // Provide a resource that is visible only to the thread context class loader.
                @Override
                public InputStream getResourceAsStream(String resourceName) {
                    return "context-only-resource".equals(resourceName)
                            ? new ByteArrayInputStream(new byte[] {1})
                            : null;
                }
            });

            // Read the fallback resource provided by the thread context class loader.
            try (InputStream resource =
                    AbstractInProcessEmbeddingModel.getResourceAsStream(modelClassLoader, "context-only-resource")) {
                assertThat(resource).isNotNull();
                assertThat(resource.read()).isEqualTo(1);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
