package dev.langchain4j;

/**
 * A controller object that allows you to abort one or more operations as and when desired.
 */
public class AbortController {
  private boolean signal;

  public boolean isAborted() {
    return signal;
  }

  public void abort() {
    this.signal = true;
  }
}
