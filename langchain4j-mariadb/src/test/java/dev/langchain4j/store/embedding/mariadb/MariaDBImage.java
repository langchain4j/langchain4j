package dev.langchain4j.store.embedding.mariadb;

import org.testcontainers.utility.DockerImageName;

public class MariaDBImage {
    protected static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("mariadb:11.7-rc");
}