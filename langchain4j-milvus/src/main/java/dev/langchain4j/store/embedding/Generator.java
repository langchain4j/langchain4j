package dev.langchain4j.store.embedding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

class Generator {

    public static String generateRandomId() {
        return UUID.randomUUID().toString();
    }

    public static List<String> generateRandomIds(int size) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ids.add(generateRandomId());
        }

        return ids;
    }

    public static List<String> generateEmptyScalars(int size) {
        String[] arr = new String[size];
        Arrays.fill(arr, "");

        return Arrays.asList(arr);
    }
}
