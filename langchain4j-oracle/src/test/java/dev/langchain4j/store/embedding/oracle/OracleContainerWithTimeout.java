package dev.langchain4j.store.embedding.oracle;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class OracleContainerWithTimeout extends OracleContainer {

  public OracleContainerWithTimeout(String dockerImageName) {
    this(DockerImageName.parse(dockerImageName));
  }

  public OracleContainerWithTimeout(DockerImageName dockerImageName) {
    super(dockerImageName);
  }

  @Override
  public OracleContainer withStartupTimeoutSeconds(int startupTimeoutSeconds) {
    this.waitStrategy.withStartupTimeout(Duration.ofSeconds(startupTimeoutSeconds));
    return super.withStartupTimeoutSeconds(startupTimeoutSeconds);
  }
}
