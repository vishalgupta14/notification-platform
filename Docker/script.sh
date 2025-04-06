#!/bin/bash

# Start MongoDB container
echo "Starting MongoDB..."
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -v mongo_data:/data/db \
  mongo:latest

# Start ActiveMQ Artemis container
echo "Starting ActiveMQ Artemis..."
docker run -d \
  --name artemis \
  -e ARTEMIS_USERNAME=admin \
  -e ARTEMIS_PASSWORD=admin \
  -p 61616:61616 \
  -p 8161:8161 \
  vromero/activemq-artemis

# Wait a few seconds to ensure services are up
sleep 5

# Show running containers
echo "Running containers:"
docker ps