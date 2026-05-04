package dev.langchain4j.model.deliverance;

import java.io.File;

class DeliveranceTestUtils {

    static final String GEMMA_MODEL_NAME = "tjake/gemma-2-2b-it-JQ4";
    static final String EMBEDDING_MODEL_NAME = "MongoDB/mdbr-leaf-ir";

    private DeliveranceTestUtils() {
    }

    static File tempDir() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "deliverance_tests");
        tmpDir.mkdirs();
        return tmpDir;
    }
}
