#!/usr/bin/env bash

# Execute this script to deploy the needed Azure OpenAI models to execute the integration tests.
# For this, you need Azure CLI installed: https://learn.microsoft.com/cli/azure/install-azure-cli

echo "Setting up environment variables..."
echo "----------------------------------"
PROJECT="langchain4j"
RESOURCE_GROUP="rg-$PROJECT"
LOCATION="swedencentral"
TAG="$PROJECT"
AI_SERVICE="ai-$PROJECT"

echo "Creating the resource group..."
echo "------------------------------"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --tags system="$TAG"

echo "Creating the Cognitive Service..."
echo "---------------------------------"
az cognitiveservices account create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --custom-domain "$AI_SERVICE" \
  --tags system="$TAG" \
  --kind "OpenAI" \
  --sku "S0"
  
# If you want to know the available models, run the following Azure CLI command:
# az cognitiveservices account list-models --resource-group "$RESOURCE_GROUP" --name "$AI_SERVICE" -o table  

echo "Deploying a gpt-35-turbo model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-35-turbo" \
  --model-name "gpt-35-turbo" \
  --model-version "1106"  \
  --model-format "OpenAI" \
  --sku-capacity 120 \
  --sku-name "Standard"

echo "Deploying a gpt-35-turbo-instruct model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-35-turbo-instruct" \
  --model-name "gpt-35-turbo-instruct" \
  --model-version "0914"  \
  --model-format "OpenAI" \
  --sku-capacity 120 \
  --sku-name "Standard"

echo "Deploying a gpt-4 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "gpt-4" \
  --model-name "gpt-4" \
  --model-version "1106-Preview"  \
  --model-format "OpenAI" \
  --sku-capacity 10 \
  --sku-name "Standard"

echo "Deploying a text-embedding-ada-002 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "text-embedding-ada-002" \
  --model-name "text-embedding-ada-002" \
  --model-version "2"  \
  --model-format "OpenAI" \
  --sku-capacity 120 \
  --sku-name "Standard"

echo "Deploying a dall-e-3 model..."
echo "----------------------"
az cognitiveservices account deployment create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --deployment-name "dall-e-3" \
  --model-name "dall-e-3" \
  --model-version "3.0"  \
  --model-format "OpenAI" \
  --sku-capacity 1 \
  --sku-name "Standard"

echo "Storing the key and endpoint in environment variables..."
echo "--------------------------------------------------------"
AZURE_OPENAI_KEY=$(
  az cognitiveservices account keys list \
    --name "$AI_SERVICE" \
    --resource-group "$RESOURCE_GROUP" \
    | jq -r .key1
  )
AZURE_OPENAI_ENDPOINT=$(
  az cognitiveservices account show \
    --name "$AI_SERVICE" \
    --resource-group "$RESOURCE_GROUP" \
    | jq -r .properties.endpoint
  )

echo "AZURE_OPENAI_KEY=$AZURE_OPENAI_KEY"
echo "AZURE_OPENAI_ENDPOINT=$AZURE_OPENAI_ENDPOINT"
