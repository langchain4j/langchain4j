package dev.langchain4j.store.embedding.oracle;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class OracleContainerWithTimeout extends OracleContainer {

  private int startupTimeoutSeconds = 60;

  private int connectTimeoutSeconds = 60;

  public OracleContainerWithTimeout(String dockerImageName) {
    this(DockerImageName.parse(dockerImageName));
  }

  public OracleContainerWithTimeout(DockerImageName dockerImageName) {
    super(dockerImageName);
    waitingFor(
        Wait
            .forLogMessage(".*DATABASE IS READY TO USE!.*\\s", 1)
            .withStartupTimeout(Duration.ofSeconds(startupTimeoutSeconds))
    );
    withConnectTimeoutSeconds(connectTimeoutSeconds);
  }

  @Override
  public OracleContainer withStartupTimeoutSeconds(int startupTimeoutSeconds) {
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    return super.withStartupTimeoutSeconds(startupTimeoutSeconds);
  }

  @Override
  public OracleContainer withConnectTimeoutSeconds(int connectTimeoutSeconds) {
    this.connectTimeoutSeconds = connectTimeoutSeconds;
    return super.withConnectTimeoutSeconds(connectTimeoutSeconds);
  }
}
