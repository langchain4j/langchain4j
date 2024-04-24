---
sidebar_position: 2
---

# Astra DB

[Astra DB](https://www.datastax.com/products/datastax-astra) is a cloud-native, fully managed database-as-a-service (DBaaS) based on Apache Cassandra. It provides a scalable, performant and highly available database solution without the operational overhead of managing Cassandra clusters. Astra supports both SQL and NoSQL APIs, and includes features like backup and restore, monitoring and alerting, and access control. It enables developers to focus on application development while the database infrastructure is managed by Datastax.

## 1. Pre-requisites

### 1.1. Sign up for Astra DB

- Access [https://astra.datastax.com](https://astra.datastax.com) and register with `Google` or `Github` account. It is free to use. There is free forever tiers of up to 25$ of consumption every month.

![](https://awesome-astra.github.io/docs/img/astra/astra-signin-github-0.png)

### 1.2. Create a Database

> If you are creating a new account, you will be brought to the DB-creation form directly.

- Get to the databases dashboard (by clicking on Databases in the left-hand navigation bar, expanding it if necessary), and click the `[Create Database]` button on the right.

![](https://datastaxdevs.github.io/langchain4j/langchain4j-1.png)

- **ℹ️ Fields Description**

| Field                                      | Description                                                                                                                                                                                                                                   |
|--------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Vector Database vs Serverless Database** | Choose `Vector Database` In june 2023, Cassandra introduced the support of vector search to enable Generative AI use cases.                                                                                                                   |
| **Database name**                          | It does not need to be unique, is not used to initialize a connection, and is only a label (keep it between 2 and 50 characters). It is recommended to have a database for each of your applications. The free tier is limited to 5 databases. |
| **Cloud Provider**                         | Choose whatever you like. Click a cloud provider logo, pick an Area in the list and finally pick a region. We recommend choosing a region that is closest to you to reduce latency. In free tier, there is very little difference.            |
| **Cloud Region**                           | Pick region close to you available for selected cloud provider and your plan.                                                                                                                                                                 |

If all fields are filled properly, clicking the "Create Database" button will start the process. 

![](https://datastaxdevs.github.io/langchain4j/langchain4j-2.png)

It should take a couple of minutes for your database to become `Active`.

![](https://datastaxdevs.github.io/langchain4j/langchain4j-3.png)

### 1.3. Get your credentials

To connect to your database, you need the API Endpoint and a token. The api endpoint is available on the database screen, there is a little icon to copy the URL in your clipboard. (it should look like `https://<db-id>-<db-region>.apps.astra.datastax.com`).

![](https://datastaxdevs.github.io/langchain4j/langchain4j-4.png)

To get a token click the `[Generate Token]` button on the right. It will generate a token that you can copy to your clipboard.

![](https://datastaxdevs.github.io/langchain4j/langchain4j-5.png)

## 2. AstraDB `EmbeddingStore`

> The full documentation regarding AstraDB can be found [here](https://docs.datastax.com/en/astra/astra-db-vector/api-reference/dataapiclient.html).
 
### 2.1. Setup your Collections

- Client initialization

```java
String token = System.getenv("ASTRA_DB_APPLICATION_TOKEN");
String apiEndpoint = System.getenv("ASTRA_DB_API_ENDPOINT");

// Initialize the client. The keyspace parameter is optional if you use
// "default_keyspace".
DataAPIClient client = new DataAPIClient(token);

// Accessing the Database
Database db = client.getDatabase(astraApiEndpoint);
```

### 2.2. Ingestion

### 2.3. Vector Search

### 2.4. Meta-Data Filtering

