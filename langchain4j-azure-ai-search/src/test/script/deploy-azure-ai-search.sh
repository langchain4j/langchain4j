#!/usr/bin/env bash

# Execute this script to deploy the necessary Azure AI Search resources to execute the integration tests.
#
# For this, you need to have Azure CLI installed: https://learn.microsoft.com/cli/azure/install-azure-cli
#
# Azure CLI runs on:
# - Windows (using Windows Command Prompt (CMD), PowerShell, or Windows Subsystem for Linux (WSL)): https://learn.microsoft.com/cli/azure/install-azure-cli-windows 
# - macOS: https://learn.microsoft.com/cli/azure/install-azure-cli-macos
# - Linux: https://learn.microsoft.com/cli/azure/install-azure-cli-linux
# - Docker: https://learn.microsoft.com/cli/azure/run-azure-cli-docker
#
# Once installed, you can run the following commands to check your installation is correct:
# az --version
# az --help

echo "Setting up environment variables..."
echo "----------------------------------"
PROJECT="langchain4j-ai-search"
RESOURCE_GROUP="rg-$PROJECT"
LOCATION="eastus"
AI_SEARCH_SERVICE="ai-$PROJECT"
TAG="$PROJECT"

echo "Creating the resource group..."
echo "------------------------------"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --tags system="$TAG"

echo "Creating the AI Search Service..."
echo "---------------------------------"
az search service create \
  --name "$AI_SEARCH_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --sku Standard \
  --partition-count 1 \
  --replica-count 1

echo "Storing the key and endpoint in environment variables..."
echo "--------------------------------------------------------"
AZURE_SEARCH_ENDPOINT="https://$AI_SEARCH_SERVICE.search.windows.net"
AZURE_SEARCH_KEY=$(
    az search admin-key show \
      --service-name "$AI_SEARCH_SERVICE" \
      --resource-group "$RESOURCE_GROUP" \
      | jq -r .primaryKey
)

echo "AZURE_SEARCH_ENDPOINT=$AZURE_SEARCH_ENDPOINT"
echo "AZURE_SEARCH_KEY=$AZURE_SEARCH_KEY"

# Once you finish the tests, you can delete the resource group with the following command:
echo "Deleting the resource group..."
echo "------------------------------"
az group delete \
  --name "$RESOURCE_GROUP" \
  --yes
