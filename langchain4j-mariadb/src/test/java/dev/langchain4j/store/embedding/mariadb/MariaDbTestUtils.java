package dev.langchain4j.store.embedding.mariadb;

import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MariaDbTestUtils {
    private static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("mariadb:11.7-rc");

    static final MariaDBContainer<?> defaultContainer = new MariaDBContainer<>(DEFAULT_IMAGE).withReuse(true);
}
