package dev.langchain4j.experimental.graph.model;

import lombok.Data;

/**
 * @Author: hangwei.zhang on 2024/7/1
 * @Version: 1.0
 **/
@Data
public class BaseState {
    /**
     * use origin input
     * */
    private String question;

    /**
     * this step generate
     * */
    private String generate;
}
