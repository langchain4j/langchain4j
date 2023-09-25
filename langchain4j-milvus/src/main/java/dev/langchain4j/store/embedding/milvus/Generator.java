package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.internal.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Generator {

    static List<String> generateRandomIds(int size) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ids.add(Utils.randomUUID());
        }

        return ids;
    }

    static List<String> generateEmptyScalars(int size) {
        String[] arr = new String[size];
        Arrays.fill(arr, "");

        return Arrays.asList(arr);
    }
}
