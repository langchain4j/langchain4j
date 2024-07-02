package dev.langchain4j.experimental.graph;

import dev.langchain4j.experimental.graph.Invoker;

import java.util.Map;
import java.util.function.Function;

/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
public interface Node<IN, OUT> extends Invoker<IN, OUT> {

    /**
     * add edge
     *
     * @param next next node
     * @param name next node name
     */
    void addNext(Node<?, ?> next, String name) throws IllegalArgumentException;

    /**
     * add edge
     *
     * @return next step nodes
     */
    Map<String, Node<?, ?>> getNextStep();

    /**
     * remove edge
     *
     * @param name next node name
     */
    void removeNext(String name);

    /**
     * add condition edge
     *
     * @param router router
     */
    void addConditionEdge(Function<?, String> router);

    Function<?, String> getConditionEdge();

    /**
     * node name
     *
     * @return node name
     */
    String name();
}
