package dev.langchain4j.store.embedding.mongodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class QueryMapping {

    private static final Logger log = LoggerFactory.getLogger(QueryMapping.class);

    public List<Double> asDoublesList(float[] input) {

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < input.length; i++) {
            double d = input[i];
            result.add(d);
        }

        return result;
    }
}
