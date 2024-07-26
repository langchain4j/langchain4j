# Oracle Database Embedding Store
This module implements `EmbeddingStore` using Oracle Database.

## Requirements
- Oracle Database 23.4 or newer

## Installation
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artificatId>langchain4j-oracle</artificatId>
    <version>0.1.0</version>
</dependency>
```

## Running the Test Suite
By default, integration tests will
[run a docker image of Oracle Database using TestContainers](https://java.testcontainers.org/modules/databases/oraclefree/).
Alternatively, the tests can connect to an Oracle Database if the following environment variables are configured:
- ORACLE_JDBC_URL : Set to an [Oracle JDBC URL](https://docs.oracle.com/en/database/oracle/oracle-database/23/jjdbc/data-sources-and-URLs.html#GUID-C4F2CA86-0F68-400C-95DA-30171C9FB8F0), such as `jdbc:oracle:thin@example:1521/serviceName`
- ORACLE_JDBC_USER : Set to the name of a database user. (Optional)
- ORACLE_JDBC_PASSWORD : Set to the password of a database user. (Optional)
