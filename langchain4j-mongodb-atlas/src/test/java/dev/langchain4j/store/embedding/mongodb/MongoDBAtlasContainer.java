package dev.langchain4j.store.embedding.mongodb;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public class MongoDBAtlasContainer extends GenericContainer<MongoDBAtlasContainer> {

    public MongoDBAtlasContainer() {
        super("mongodb/atlas:latest");
        withPrivilegedMode(true);
        withCommand("/bin/bash", "-c", "atlas deployments setup local-test --type local --port 27778 --bindIpAll --username root --password root --force && tail -f /dev/null");
        withExposedPorts(27778);
        waitingFor(Wait.forLogMessage(".*Deployment created!.*\\n", 1));
        withStartupTimeout(Duration.ofMinutes(10));
    }

    public String getConnectionString() {
        return String.format("mongodb://%s:%s/?directConnection=true", getHost(), getMappedPort(27778));
    }

}
