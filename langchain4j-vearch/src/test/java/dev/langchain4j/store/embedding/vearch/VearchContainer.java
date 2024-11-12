package dev.langchain4j.store.embedding.vearch;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

public class VearchContainer extends GenericContainer<VearchContainer> {

    public VearchContainer() {
        super("vearch/vearch:latest");
        withExposedPorts(9001, 8817);
        withCommand("all");
        withCopyFileToContainer(MountableFile.forClasspathResource("config.toml"), "/vearch/config.toml");
    }
}
