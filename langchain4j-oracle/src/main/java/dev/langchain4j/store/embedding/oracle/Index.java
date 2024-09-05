package dev.langchain4j.store.embedding.oracle;

/**
 * Abstract class for that represents an Index. It is extended by {@link IVFIndex} and
 * {@link JSONIndex}.
 */
public abstract class Index {
  private CreateOption createOption;
  private String createIndexStatement;
  private String dropIndexStatement;

  Index(CreateOption createOption, String createIndexStatement, String dropIndexStatement) {
    this.createIndexStatement = createIndexStatement;
    this.dropIndexStatement = dropIndexStatement;
    this.createOption = createOption;
  }

  /**
   * @return The CREATE INDEX statement.
   */
  public String getCreateStatement() {
    return createIndexStatement;
  }

  /**
   * @return The DROP INDEX statement.
   */
  public String getDropStatement() {
    return dropIndexStatement;
  }

  /**
   * @return Returns true if the index should be created.
   */
  public boolean createIndex() {
    return createOption != CreateOption.DO_NOT_CREATE;
  }

  /**
   * @return Returns true if the index should be dropped before being created.
   */
  public boolean dropIndex() {
    return createOption == CreateOption.CREATE_OR_REPLACE;
  }

}
