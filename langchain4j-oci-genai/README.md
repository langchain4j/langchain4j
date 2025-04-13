# Oracle Cloud Infrastructure GenAI
This module implements `ChatModels` with [OCI GenAI](https://www.oracle.com/artificial-intelligence/generative-ai/generative-ai-service) 
over official OCI SDK.

## Requirements
Oracle OCI tenancy with GenAi models available.
See AI model availability [here](https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/Content/generative-ai/pretrained-models.htm). 

## Installation
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artificatId>langchain4j-oci-genai</artificatId>
</dependency>
```
Additionally, you have to select HTTP client for OCI SDK, by default, use a Jersey 3 based version:
```xml
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-common-httpclient-jersey3</artifactId>
    <version>${oci-sdk.version}</version>
</dependency>
```

In case use are on **Java EE/Jakarta EE 8 or older** runtime, please use Jersey 2 based version:
```xml
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-common-httpclient-jersey</artifactId>
    <version>${oci-sdk.version}</version>
</dependency>
```

More information can be found in [OCI SDK documentation](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk3.htm#javasdk3__HTTP-client-libraries).




## Usage
Minimal required configuration:
* chat model name or OCID - Find models available in your tenancy in your OCI Console in Generative AI section
* compartment id - OCID of the [compartment](https://docs.oracle.com/en/cloud/foundation/cloud_architecture/governance/compartments.html) with GenAi model you want to use
* authentication provider - authentication provider used by [OCI SDK](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm), see [configuring credential section](https://docs.public.oneportal.content.oci.oraclecloud.com/en-us/iaas/Content/API/SDKDocs/javasdkgettingstarted.htm#Configur__ConfigCreds) of SDK docs.

```java
ChatModel chatModel = OciGenAiChatModel.builder()
        .authProvider(new ConfigFileAuthenticationDetailsProvider("DEFAULT")) // OCI SDK Authentication provider
        .chatModelId("meta.llama-3.3-70b-instruct")                           // Model name or OCID
        .compartmentId("ocid1.tenancy.oc1..your.compartment.ocid")            // Compartment OCID
        .region(Region.EU_FRANKFURT_1)
        .build();
```

Available chat models APIs:
* `OciGenAiChatModel` - for all OCI GenAi generic chat models(llama)
* `OciGenAiStreamingChatModel` - streaming API for OCI GenAi generic chat models
* `OciGenAiCohereChatModel` - for all OCI GenAi Cohere chat models
* `OciGenAiCohereStreamingChatModel` - streaming API for OCI GenAi Cohere chat models

## Running tests
Integration tests are disabled unless following environment variables are available:

* `OCI_GENAI_COMPARTMENT_ID` - OCI compartment ID(OCID) for compartment with available on-demand GenAi models available 
* `OCI_GENAI_GENERIC_MODEL_NAME` - Generic on-demand GenAi model(non-cohere) name or OCID available at provided compartment
* `OCI_GENAI_COHERE_MODEL_NAME` - Cohere on-demand GenAi model name or OCID available at provided compartment

Image test is disabled unless additional environment property is set:
* `OCI_GENAI_GENERIC_VISION_MODEL_NAME` - Generic on-demand GenAi vision model(non-cohere) name or OCID available at provided compartment

Optional variables:
* `OCI_GENAI_CONFIG_PROFILE` - [OCI configuration file](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm) profile, `DEFAULT` is used if not set
