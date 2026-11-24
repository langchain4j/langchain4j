package dev.langchain4j.service;

import static dev.langchain4j.service.TypeUtils.typeHasRawClass;

import dev.langchain4j.spi.services.PublisherAdapter;
import java.lang.reflect.Type;
import java.util.concurrent.Flow;

/**
 * Test {@link PublisherAdapter} for {@link ReactiveBox}, registered via
 * {@code META-INF/services/dev.langchain4j.spi.services.PublisherAdapter}.
 */
public class ReactiveBoxAdapter implements PublisherAdapter {

    @Override
    public boolean canAdapt(Type type) {
        return typeHasRawClass(type, ReactiveBox.class);
    }

    @Override
    public Object fromPublisher(Type type, Flow.Publisher<?> publisher) {
        return new ReactiveBox<>(publisher);
    }
}
