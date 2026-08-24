package dev.langchain4j.model.embedding.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class AbstractInProcessEmbeddingModelTest {

    // 验证模型资源不依赖线程上下文类加载器。
    @Test
    void should_load_resource_when_context_class_loader_cannot_find_it() throws IOException {
        // 保存原始线程上下文类加载器。
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        // 获取定义模型类的类加载器。
        ClassLoader modelClassLoader = AbstractInProcessEmbeddingModelTest.class.getClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {});

            // 读取仅由模型类加载器可见的资源。
            try (InputStream resource =
                    AbstractInProcessEmbeddingModel.getResourceAsStream(modelClassLoader, "bert-tokenizer.json")) {
                assertThat(resource).isNotNull();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    // 验证模型类加载器找不到资源时仍会回退到线程上下文类加载器。
    @Test
    void should_fall_back_to_context_class_loader() throws IOException {
        // 保存原始线程上下文类加载器。
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        // 创建无法读取回退资源的模型类加载器。
        ClassLoader modelClassLoader = new ClassLoader(null) {};

        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
                // 为测试提供仅在线程上下文类加载器中存在的资源。
                @Override
                public InputStream getResourceAsStream(String resourceName) {
                    return "context-only-resource".equals(resourceName)
                            ? new ByteArrayInputStream(new byte[] {1})
                            : null;
                }
            });

            // 读取线程上下文类加载器提供的回退资源。
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
