package dev.langchain4j.experimental.graph;

import com.google.common.collect.Maps;
import dev.langchain4j.experimental.graph.Node;

import java.util.Map;
import java.util.function.Function;

/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
public abstract class AbstractNode<IN, OUT> implements Node<IN, OUT> {
    private final Map<String,Node<?,?>> nextStepNodes;

    private  Function<?,String> router;

    public AbstractNode(int size){
        nextStepNodes= Maps.newHashMapWithExpectedSize(size>0?size:1);
    }

    @Override
    public void addNext(Node<?, ?> next, String name) throws IllegalArgumentException {
        if(nextStepNodes.containsKey(name)){
            throw new IllegalArgumentException("input node already exist current step node name "+name());
        }
        nextStepNodes.put(name, next);
    }

    @Override
    public void addConditionEdge(Function<?, String> router) {
        this.router=router;
    }

    @Override
    public void removeNext(String name) {
        if(!nextStepNodes.containsKey(name)){
            throw new IllegalArgumentException("input node name do not exist current step node name "+name());
        }
        nextStepNodes.remove(name);
    }

    @Override
    public Function<?, String> getConditionEdge() {
        return router;
    }

    @Override
    public Map<String, Node<?, ?>> getNextStep() {
        return nextStepNodes;
    }
}
