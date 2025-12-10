package dev.langchain4j.mcp.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.mcp.registryclient.model.McpEnvironmentVariable;
import dev.langchain4j.mcp.registryclient.model.McpPackage;
import dev.langchain4j.mcp.registryclient.model.McpPackageArgument;
import dev.langchain4j.mcp.registryclient.model.McpRuntimeArgument;
import dev.langchain4j.mcp.registryclient.model.McpServer;
import dev.langchain4j.mcp.registryclient.model.McpVariable;
import org.junit.jupiter.api.Test;

public class RegistryClientResponsesParsingTest {

    @Test
    public void serverWithMultiplePackages() throws JsonProcessingException {
        // taken from https://github.com/modelcontextprotocol/registry/blob/fadbba2364009b9226070f94dd5ed802766a0afa/docs/reference/server-json/generic-server-json.md#filesystem-server-with-multiple-packages
        String json = """
                {
                  "$schema": "https://static.modelcontextprotocol.io/schemas/2025-10-17/server.schema.json",
                  "name": "io.github.modelcontextprotocol/filesystem",
                  "description": "Node.js server implementing Model Context Protocol (MCP) for filesystem operations.",
                  "title": "Filesystem",
                  "repository": {
                    "url": "https://github.com/modelcontextprotocol/servers",
                    "source": "github",
                    "id": "b94b5f7e-c7c6-d760-2c78-a5e9b8a5b8c9"
                  },
                  "version": "1.0.2",
                  "packages": [
                    {
                      "registryType": "npm",
                      "registryBaseUrl": "https://registry.npmjs.org",
                      "identifier": "@modelcontextprotocol/server-filesystem",
                      "version": "1.0.2",
                      "transport": {
                        "type": "stdio"
                      },
                      "packageArguments": [
                        {
                          "type": "positional",
                          "valueHint": "target_dir",
                          "description": "Path to access",
                          "default": "/Users/username/Desktop",
                          "isRequired": true,
                          "isRepeated": true
                        }
                      ],
                      "environmentVariables": [
                        {
                          "name": "LOG_LEVEL",
                          "description": "Logging level (debug, info, warn, error)",
                          "default": "info"
                        }
                      ]
                    },
                    {
                      "registryType": "oci",
                      "identifier": "docker.io/mcp/filesystem:1.0.2",
                      "transport": {
                        "type": "stdio"
                      },
                      "runtimeArguments": [
                        {
                          "type": "named",
                          "description": "Mount a volume into the container",
                          "name": "--mount",
                          "value": "type=bind,src={source_path},dst={target_path}",
                          "isRequired": true,
                          "isRepeated": true,
                          "variables": {
                            "source_path": {
                              "description": "Source path on host",
                              "format": "filepath",
                              "isRequired": true
                            },
                            "target_path": {
                              "description": "Path to mount in the container. It should be rooted in `/project` directory.",
                              "isRequired": true,
                              "default": "/project"
                            }
                          }
                        }
                      ],
                      "packageArguments": [
                        {
                          "type": "positional",
                          "valueHint": "target_dir",
                          "value": "/project"
                        }
                      ],
                      "environmentVariables": [
                        {
                          "name": "LOG_LEVEL",
                          "description": "Logging level (debug, info, warn, error)",
                          "default": "info"
                        }
                      ]
                    }
                  ],
                  "_meta": {
                    "io.modelcontextprotocol.registry/publisher-provided": {
                      "tool": "ci-publisher",
                      "version": "3.2.1",
                      "build_info": {
                        "commit": "a1b2c3d4e5f6789",
                        "timestamp": "2023-12-01T10:30:00Z",
                        "pipeline_id": "filesystem-build-789",
                        "environment": "production"
                      }
                    }
                  }
                }
                """;
        McpServer server = DefaultMcpRegistryClient.OBJECT_MAPPER.readValue(json, McpServer.class);

        // Verify server is not null
        assertThat(server).isNotNull();

        // Verify server-level fields
        assertThat(server.getSchema()).isEqualTo("https://static.modelcontextprotocol.io/schemas/2025-10-17/server.schema.json");
        assertThat(server.getName()).isEqualTo("io.github.modelcontextprotocol/filesystem");
        assertThat(server.getDescription()).isEqualTo("Node.js server implementing Model Context Protocol (MCP) for filesystem operations.");
        assertThat(server.getVersion()).isEqualTo("1.0.2");

        // Verify repository fields
        assertThat(server.getRepository()).isNotNull();
        assertThat(server.getRepository().getUrl()).isEqualTo("https://github.com/modelcontextprotocol/servers");
        assertThat(server.getRepository().getSource()).isEqualTo("github");
        assertThat(server.getRepository().getId()).isEqualTo("b94b5f7e-c7c6-d760-2c78-a5e9b8a5b8c9");

        // Verify there are 2 packages
        assertThat(server.getPackages()).isNotNull().hasSize(2);

        // ===== First Package (NPM) =====
        McpPackage npmPackage = server.getPackages().get(0);
        assertThat(npmPackage.getRegistryType()).isEqualTo("npm");
        assertThat(npmPackage.getRegistryBaseUrl()).isEqualTo("https://registry.npmjs.org");
        assertThat(npmPackage.getIdentifier()).isEqualTo("@modelcontextprotocol/server-filesystem");
        assertThat(npmPackage.getVersion()).isEqualTo("1.0.2");

        // Verify npm package transport
        assertThat(npmPackage.getTransport()).isNotNull();
        assertThat(npmPackage.getTransport().getType()).isEqualTo("stdio");

        // Verify npm package arguments
        assertThat(npmPackage.getPackageArguments()).isNotNull().hasSize(1);
        McpPackageArgument npmPackageArg = npmPackage.getPackageArguments().get(0);
        assertThat(npmPackageArg.getType()).isEqualTo("positional");
        assertThat(npmPackageArg.getValueHint()).isEqualTo("target_dir");
        assertThat(npmPackageArg.getDescription()).isEqualTo("Path to access");
        assertThat(npmPackageArg.getDefaultValue()).isEqualTo("/Users/username/Desktop");
        assertThat(npmPackageArg.isRequired()).isTrue();
        assertThat(npmPackageArg.isRepeated()).isTrue();

        // Verify npm package environment variables
        assertThat(npmPackage.getEnvironmentVariables()).isNotNull().hasSize(1);
        McpEnvironmentVariable npmEnvVar = npmPackage.getEnvironmentVariables().get(0);
        assertThat(npmEnvVar.getName()).isEqualTo("LOG_LEVEL");
        assertThat(npmEnvVar.getDescription()).isEqualTo("Logging level (debug, info, warn, error)");
        assertThat(npmEnvVar.getDefaultValue()).isEqualTo("info");

        // ===== Second Package (OCI) =====
        McpPackage ociPackage = server.getPackages().get(1);
        assertThat(ociPackage.getRegistryType()).isEqualTo("oci");
        assertThat(ociPackage.getIdentifier()).isEqualTo("docker.io/mcp/filesystem:1.0.2");

        // Verify oci package transport
        assertThat(ociPackage.getTransport()).isNotNull();
        assertThat(ociPackage.getTransport().getType()).isEqualTo("stdio");

        // Verify oci runtime arguments
        assertThat(ociPackage.getRuntimeArguments()).isNotNull().hasSize(1);
        McpRuntimeArgument runtimeArg = ociPackage.getRuntimeArguments().get(0);
        assertThat(runtimeArg.getType()).isEqualTo("named");
        assertThat(runtimeArg.getDescription()).isEqualTo("Mount a volume into the container");
        assertThat(runtimeArg.getName()).isEqualTo("--mount");
        assertThat(runtimeArg.getValue()).isEqualTo("type=bind,src={source_path},dst={target_path}");
        assertThat(runtimeArg.isRequired()).isTrue();
        assertThat(runtimeArg.isRepeated()).isTrue();

        // Verify runtime argument variables
        assertThat(runtimeArg.getVariables()).isNotNull().hasSize(2);
        McpVariable sourcePath = runtimeArg.getVariables().get("source_path");
        assertThat(sourcePath).isNotNull();
        assertThat(sourcePath.getDescription()).isEqualTo("Source path on host");
        assertThat(sourcePath.getFormat()).isEqualTo("filepath");
        assertThat(sourcePath.isRequired()).isTrue();

        McpVariable targetPath = runtimeArg.getVariables().get("target_path");
        assertThat(targetPath).isNotNull();
        assertThat(targetPath.getDescription()).isEqualTo("Path to mount in the container. It should be rooted in `/project` directory.");
        assertThat(targetPath.isRequired()).isTrue();
        assertThat(targetPath.getDefaultValue()).isEqualTo("/project");

        // Verify oci package arguments
        assertThat(ociPackage.getPackageArguments()).isNotNull().hasSize(1);
        McpPackageArgument ociPackageArg = ociPackage.getPackageArguments().get(0);
        assertThat(ociPackageArg.getType()).isEqualTo("positional");
        assertThat(ociPackageArg.getValueHint()).isEqualTo("target_dir");
        assertThat(ociPackageArg.getValue()).isEqualTo("/project");

        // Verify oci package environment variables
        assertThat(ociPackage.getEnvironmentVariables()).isNotNull().hasSize(1);
        McpEnvironmentVariable ociEnvVar = ociPackage.getEnvironmentVariables().get(0);
        assertThat(ociEnvVar.getName()).isEqualTo("LOG_LEVEL");
        assertThat(ociEnvVar.getDescription()).isEqualTo("Logging level (debug, info, warn, error)");
        assertThat(ociEnvVar.getDefaultValue()).isEqualTo("info");

        // Verify meta information
        assertThat(server.getMeta()).isNotNull();
        assertThat(server.getMeta().getPublisherProvided()).isNotNull();
        assertThat(server.getMeta().getPublisherProvided()).containsKey("tool");
        assertThat(server.getMeta().getPublisherProvided().get("tool").asText()).isEqualTo("ci-publisher");
        assertThat(server.getMeta().getPublisherProvided()).containsKey("version");
        assertThat(server.getMeta().getPublisherProvided().get("version").asText()).isEqualTo("3.2.1");
        assertThat(server.getMeta().getPublisherProvided()).containsKey("build_info");
    }

}
