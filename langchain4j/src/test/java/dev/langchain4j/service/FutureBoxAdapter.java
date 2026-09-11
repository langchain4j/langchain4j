package dev.langchain4j.service;

import static dev.langchain4j.service.TypeUtils.typeHasRawClass;

import dev.langchain4j.spi.services.CompletableFutureAdapter;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

/**
 * Test {@link CompletableFutureAdapter} for {@link FutureBox}, registered via
 * {@code META-INF/services/dev.langchain4j.spi.services.CompletableFutureAdapter}.
 */
public class FutureBoxAdapter implements CompletableFutureAdapter {

    @Override
    public boolean canAdapt(Type type) {
        return typeHasRawClass(type, FutureBox.class);
    }

    @Override
    public CompletableFuture<?> toCompletableFuture(Object asyncValue) {
        return ((FutureBox<?>) asyncValue).future();
    }

    @Override
    public Object fromCompletableFuture(Type type, CompletableFuture<?> future) {
        return new FutureBox<>(future);
    }
}
