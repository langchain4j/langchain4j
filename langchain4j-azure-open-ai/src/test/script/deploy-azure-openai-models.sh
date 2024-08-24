#!/usr/bin/env bash

# Execute this script to deploy the needed Azure OpenAI models to execute the integration tests.
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

PROJECT="langchain4j-swedencentral"
RESOURCE_GROUP="rg-$PROJECT"
LOCATION="swedencentral"
AI_SERVICE="ai-$PROJECT"
TAG="$PROJECT"

echo "Creating the resource group..."
echo "------------------------------"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --tags system="$TAG"

# If you want to know the available SKUs, run the following Azure CLI command:
# az cognitiveservices account list-skus --location "$LOCATION"  -o table

echo "Creating the Cognitive Service..."
echo "---------------------------------"
COGNITIVE_SERVICE_ID=$(az cognitiveservices account create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --custom-domain "$AI_SERVICE" \
  --tags system="$TAG" \
  --kind "OpenAI" \
  --sku "S0" \
   | jq -r ".id")


# Security
# - Disable API Key authentication
# - Assign a system Managed Identity to the Cognitive Service -> this is for using from other Azure services, like Azure Container Apps
# - Assign the Contributor role on this resource group to the current user, so he can use the models from the CLI (this is how tests would be normally executed)
az resource update --ids $COGNITIVE_SERVICE_ID --set properties.disableLocalAuth=true --latest-include-preview

az cognitiveservices account identity assign \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP"

PRINCIPAL_ID=$(az ad signed-in-user show --query id -o tsv)
SUBSCRIPTION_ID=$(az account show --query id -o tsv)

az role assignment create \
        --role "5e0bd9bd-7b93-4f28-af87-19fc36ad61bd" \
        --assignee-object-id "$PRINCIPAL_ID" \
        --scope /subscriptions/"$SUBSCRIPTION_ID"/resourceGroups/"$RESOURCE_GROUP" \
        --assignee-principal-type User

# If you want to know the available models, run the following Azure CLI command:
# az cognitiveservices account list-models --resource-group "$RESOURCE_GROUP" --name "$AI_SERVICE" -o table  

# Chat Models
echo "Deploying Chat Models"
echo "====================="

echo "Deploying a gpt-35-turbo-0613 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-35-turbo-0613" \
  --model-name "gpt-35-turbo" \
  --model-version "0613"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a gpt-35-turbo-1106 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-35-turbo-1106" \
  --model-name "gpt-35-turbo" \
  --model-version "1106"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a gpt-35-turbo-16k-0613 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-35-turbo-16k-0613" \
  --model-name "gpt-35-turbo-16k" \
  --model-version "0613"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a gpt-4-0613 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-4-0613" \
  --model-name "gpt-4" \
  --model-version "0613"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a gpt-4-turbo-2024-04-09 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-4-turbo-2024-04-09" \
  --model-name "gpt-4" \
  --model-version "turbo-2024-04-09"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a gpt-4-32k-0613 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-4-32k-0613" \
  --model-name "gpt-4-32k" \
  --model-version "0613"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a gpt-4-vision-preview model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-4-vision-preview" \
  --model-name "gpt-4" \
  --model-version "vision-preview"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a gpt-4o model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-4o" \
  --model-name "gpt-4o" \
  --model-version "2024-05-13"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

# Embedding Models
echo "Deploying Embedding Models"
echo "=========================="

echo "Deploying a text-embedding-ada-002-2 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "text-embedding-ada-002-2" \
  --model-name "text-embedding-ada-002" \
  --model-version "2"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a text-embedding-3-large-1 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "text-embedding-3-large-1" \
  --model-name "text-embedding-3-large" \
  --model-version "1"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

# Image Models
echo "Deploying Image Models"
echo "======================"

echo "Deploying a dall-e-3 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "dall-e-3-30" \
  --model-name "dall-e-3" \
  --model-version "3.0"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

# Language Models
echo "Deploying Language Models"
echo "========================="

echo "Deploying a gpt-35-turbo-instruct-0914 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-35-turbo-instruct-0914" \
  --model-name "gpt-35-turbo-instruct" \
  --model-version "0914"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Deploying a davinci-002-1 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "davinci-002-1" \
  --model-name "davinci-002" \
  --model-version "1"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"


echo "Storing endpoint in an environment variable..."
echo "--------------------------------------------------------"
AZURE_OPENAI_ENDPOINT=$(
  az cognitiveservices account show \
    --name "$AI_SERVICE" \
    --resource-group "$RESOURCE_GROUP" \
    | jq -r .properties.endpoint
  )

echo "AZURE_OPENAI_ENDPOINT=$AZURE_OPENAI_ENDPOINT"

# Once you finish the tests, you can delete the resource group with the following command:
echo "Deleting the resource group..."
echo "------------------------------"
az group delete \
  --name "$RESOURCE_GROUP" \
  --yes
