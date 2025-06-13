---
sidebar_position: 2
---

# Google Cloud Storage

A Google Cloud Storage (GCS) document loader that allows you to load documents from storage buckets.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-loader-google-cloud-storage</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## APIs

- `GoogleCloudStorageDocumentLoader`

## Authentication

The authentication should be handled transparently for you:
* If your application is running on Google Cloud Platform (Cloud Run, App Engine, Compute Engine, etc)
* When running locally on your machine, if you are already authenticated via Google's `gcloud` SDK

You should just create a loader specifying just your project ID:

```java
GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
    .project(System.getenv("GCP_PROJECT_ID"))
    .build();
```

Otherwise, it's possible to specify `Credentials`, if you have downloaded a service account key, and exported an environment variable pointing to it:

```java
GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
    .project(System.getenv("GCP_PROJECT_ID"))
    .credentials(GoogleCredentials.fromStream(new FileInputStream(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"))))
    .build();
```

Learn more about [credentials](https://cloud.google.com/docs/authentication/application-default-credentials).

When accessing a public bucket, you shouldn't need to authenticate.

## Examples

### Load a single file from a GCS bucket

```java
GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
    .project(System.getenv("GCP_PROJECT_ID"))
    .build();

Document document = gcsLoader.loadDocument("BUCKET_NAME", "FILE_NAME.txt", new TextDocumentParser());
```

### Load all files from a GCS bucket

```java
GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
    .project(System.getenv("GCP_PROJECT_ID"))
    .build();

List<Document> documents = gcsLoader.loadDocuments("BUCKET_NAME", new TextDocumentParser());
```

### Load all files from a GCS bucket with a glob pattern

```java
GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
    .project(System.getenv("GCP_PROJECT_ID"))
    .build();

List<Document> documents = gcsLoader.loadDocuments("BUCKET_NAME", "*.txt", new TextDocumentParser());
```

For more code samples, please have a look at the integration test class:
- [GoogleCloudStorageDocumentLoaderIT](https://github.com/langchain4j/langchain4j/blob/main/document-loaders/langchain4j-document-loader-google-cloud-storage/src/test/java/dev/langchain4j/data/document/loader/gcs/GoogleCloudStorageDocumentLoaderIT.java)
