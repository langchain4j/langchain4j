package dev.langchain4j.experimental.graph;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dev.langchain4j.Experimental;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
@Experimental
public abstract class AbstractGraph<IN, OUT> implements Graph<IN, OUT> {
    private final Map<String, Node<?, ?>> nodes;
    @Getter
    private Node<IN, ?> startNode;

    public AbstractGraph(int size) {
        nodes = Maps.newHashMapWithExpectedSize(size);
    }

    @Override
    public void addNode(Node<?, ?> node) {
        if (nodes.containsKey(node.name())) {
            throw new IllegalArgumentException("input node already exist in this graph define,please check");
        }
        this.nodes.put(node.name(), node);
    }

    @Override
    public void addEdge(String from, String to) throws IllegalArgumentException {
        Preconditions.checkArgument(this.nodes.containsKey(from), "from node is not define in this scope name: " + from);
        Preconditions.checkArgument(this.nodes.containsKey(to), "to node is not define in this scope name: " + to);
        this.nodes.get(from).addNext(this.nodes.get(to), to);
    }

    public void setStartNode(String name) {
        startNode = (Node<IN, ?>) this.nodes.get(name);
    }


    @Override
    public void addConditionEdge(String from, Function<?, String> router) {
        Preconditions.checkArgument(this.nodes.containsKey(from), "from node is not define in this scope name: " + from);
        this.nodes.get(from).addConditionEdge(router);
    }


    @Override
    public OUT invoke(IN in) {
        Node<Object, Object> curNode = (Node<Object, Object>) startNode;
        Object res = in;
        while (curNode != null) {
            if(isOpen(curNode)){
                curNode.preLog(in);
            }
            Object tempRes = curNode.invoke(res);
            if(isOpen(curNode)){
                curNode.afterLog(tempRes);
            }
            res = tempRes;
            if (MapUtils.isNotEmpty(curNode.getNextStep())) {
                /*if has router switch by router or not choose first*/
                if (Objects.nonNull(curNode.getConditionEdge())) {
                    String name = ((Function<Object, String>) curNode.getConditionEdge()).apply(tempRes);
                    curNode = (Node<Object, Object>) curNode.getNextStep().get(name);
                } else {
                    for (Map.Entry<String,Node<?, ?>> stringNodeEntry : curNode.getNextStep().entrySet()) {
                        curNode = (Node<Object, Object>) stringNodeEntry.getValue();
                        break;
                    }
                }
                continue;
            }
            curNode = null;
        }
        assert res != null;
        return (OUT) res;
    }

    private boolean isOpen(Node<?,?> node){
        return node.logSwitch();
    }


    public String generateGraph() {
        printTree(startNode, 0, "");
        return "";
    }


    private void printTree(Node<?,?> node, int depth, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("    "); // 使用四个空格作为缩进
        }
        sb.append(prefix);
        sb.append(node.name());
        // 打印当前节点
        System.out.println(sb);
        if (MapUtils.isEmpty(node.getNextStep())) {
            return;
        }
        List<Node<?, ?>> nextNodes = Lists.newArrayList(node.getNextStep().values());
        for (int i = 0; i < nextNodes.size(); i++) {
            Node<?,?> child = nextNodes.get(i);
            String subPrefix;
            // 为当前子节点添加连接线
            if (i > 0) {
                // 如果不是第一个子节点，添加垂直连接线
                subPrefix = "│   ";
            } else {
                // 如果是第一个子节点，添加倾斜连接线
                subPrefix = "└── ";
            }
            // 递归打印子节点，深度加1
            printTree(child, depth + 1, subPrefix);
        }
    }
}
