package dev.langchain4j.experimental.graph;

import dev.langchain4j.Experimental;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @Author: hangwei.zhang on 2024/6/27
 * @Version: 1.0
 * @description The abstract unified actuator facilitates unified scheduling
 **/
@Experimental
public interface Invoker<IN, OUT> {


    /**
     * simple call
     *
     * @param in input
     */
    OUT invoke(IN in);


    /**
     * batch call
     *
     * @param in input
     */
    List<OUT> invokeBatch(List<IN> in);

    /**
     * async call
     *
     * @param in input
     */
    CompletableFuture<OUT> aInvoke(IN in);

    /**
     * async call with listener
     *
     * @param in          input
     * @param sucListener success listener
     * @param failLister  fail listener
     * @see java.util.concurrent.CompletableFuture#whenComplete(java.util.function.BiConsumer)
     * @see CompletableFuture#exceptionally(Function)
     */
    CompletableFuture<OUT> aInvoke(IN in, BiConsumer<? super OUT, ? super Throwable> sucListener, Function<Throwable, ? extends OUT> failLister);

    /**
     * set executor support async invoke
     */
    ExecutorService executor();

    /**
     * log switch
     */
    default boolean logSwitch() {
        return false;
    }

    /**
     * pre log
     */
    default void preLog(IN in) {

    }

    /**
     * after log
     */
    default void afterLog(OUT out) {

    }
}
