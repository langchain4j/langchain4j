package dev.langchain4j.experimental.graph.model;

import dev.langchain4j.experimental.graph.Node;
import dev.langchain4j.experimental.graph.model.BaseState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
public class FINISH implements Node<BaseState, String> {
    @Override
    public String invoke(BaseState state) {
        return "finish answer:  " + state.getGenerate();
    }

    @Override
    public List<String> invokeBatch(List<BaseState> in) {
        return null;
    }

    @Override
    public CompletableFuture<String> aInvoke(BaseState object) {
        return null;
    }

    @Override
    public CompletableFuture<String> aInvoke(BaseState object, BiConsumer<? super String, ? super Throwable> sucListener, Function<Throwable, ? extends String> failLister) {
        return null;
    }

    @Override
    public ExecutorService executor() {
        return null;
    }

    @Override
    public void addNext(Node<?, ?> next, String name) throws IllegalArgumentException {
        throw new UnsupportedOperationException("finish node can not add node");
    }

    @Override
    public  Map<String, Node<?, ?>> getNextStep() {
        return null;
    }

    @Override
    public void removeNext(String name) {
        throw new UnsupportedOperationException("finish node can not remove node");
    }

    @Override
    public void addConditionEdge(Function<?, String> router) {
        throw new UnsupportedOperationException("finish node can not add condition node");
    }

    @Override
    public Function<?, String> getConditionEdge() {
        return null;
    }

    @Override
    public String name() {
        return "finish";
    }
}
