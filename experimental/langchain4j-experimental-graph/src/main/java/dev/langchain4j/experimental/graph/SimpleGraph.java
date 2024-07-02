package dev.langchain4j.experimental.graph;

import dev.langchain4j.experimental.graph.model.BaseState;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
public class SimpleGraph extends AbstractGraph<BaseState, String> {
    public SimpleGraph(int size) {
        super(size);
    }


    @Override
    public List<String> invokeBatch(List<BaseState> in) {
        return null;
    }

    @Override
    public CompletableFuture<String> aInvoke(BaseState state) {
        return null;
    }

    @Override
    public CompletableFuture<String> aInvoke(BaseState state, BiConsumer<? super String, ? super Throwable> sucListener, Function<Throwable, ? extends String> failLister) {
        return null;
    }

    @Override
    public ExecutorService executor() {
        return null;
    }
}
