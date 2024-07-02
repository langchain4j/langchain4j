package dev.langchain4j.experimental.graph;


import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
@Slf4j
public class NodeFactory {

    public static <IN, OUT> Node<IN, OUT> createToolNode(String name, Function<IN, OUT> executor) {
        return new SimpleNode<>(name, executor);
    }

    public static class SimpleNode<IN, OUT> extends AbstractNode<IN, OUT> {

        private final Function<IN, OUT> executor;
        private final String name;

        public SimpleNode(int size, String name, Function<IN, OUT> executor) {
            super(size);
            this.name = name;
            this.executor = executor;
        }

        public SimpleNode(String name, Function<IN, OUT> executor) {
            super(0);
            this.name = name;
            this.executor = executor;
        }

        @Override
        public OUT invoke(IN in) {
            return executor.apply(in);
        }

        @Override
        public List<OUT> invokeBatch(List<IN> in) {
            return in.stream().map(executor).collect(Collectors.toList());
        }

        @Override
        public CompletableFuture<OUT> aInvoke(IN in) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OUT> aInvoke(IN in, BiConsumer<? super OUT, ? super Throwable> sucListener, Function<Throwable, ? extends OUT> failLister) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException();
        }


        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean logSwitch() {
            return true;
        }

        @Override
        public void preLog(IN in) {
            log.info("name: [{}] invoke start input:[{}]", name(), JSON.toJSONString(in));
        }

        @Override
        public void afterLog(OUT out) {
            log.info("name: [{}] invoke end output:[{}]", name(), JSON.toJSONString(out));
        }
    }


}
