package dev.langchain4j.experimental.graph;

import dev.langchain4j.Experimental;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
@Experimental
public interface Graph<IN, OUT> extends Invoker<IN, OUT> {

    /**
     * add Node for this graph
     */
    void addNode(Node<?, ?> node) throws IllegalArgumentException;

    /**
     * add edge for this graph
     *
     * @param from start node name
     * @param to   end node name
     */
    void addEdge(String from, String to) throws IllegalArgumentException;

    /**
     * set start node for this graph
     *
     * @param name node name
     */
    void setStartNode(String name);

    /**
     * set condition edge
     *
     * @param from   start node
     * @param router condition
     */
    void addConditionEdge(String from, Function<?, String> router);

    /**
     * print graph structure base Hierarchy
     * to verify that the built graph is as expected
     * e.g.
     * chain0
     *     └── chain3
     *         └── finish
     *     │   chain2
     *         └── finish
     *     │   chain1
     *         └── finish
     */
    String generateGraph();
}
