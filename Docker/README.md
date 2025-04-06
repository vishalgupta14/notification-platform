# MessageNodeExecutor - Local Setup

This project uses **MongoDB** and **ActiveMQ Artemis** as part of its messaging and persistence infrastructure. Below are the steps to set them up using Docker.

---

## 🔧 Prerequisites

- [Docker](https://www.docker.com/products/docker-desktop) installed and running

---

## 🚀 Services Setup via Docker

### 1. 🗓️ MongoDB

Used for storing data such as transformation logs and message tracking.

#### 🔧 Configuration
```properties
spring.data.mongodb.uri=mongodb://localhost:27017/hyperflow
```

#### ▶️ Run via Docker
```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -v mongo_data:/data/db \
  mongo:latest
```

---

### 2. 📬 ActiveMQ Artemis

Used for sending and receiving messages in a decoupled way.

#### 🔧 Configuration
```properties
spring.artemis.mode=native
spring.artemis.host=localhost
spring.artemis.port=61616
spring.artemis.user=admin
spring.artemis.password=admin
```

#### ▶️ Run via Docker
```bash
docker run -d \
  --name artemis \
  -e ARTEMIS_USERNAME=admin \
  -e ARTEMIS_PASSWORD=admin \
  -p 61616:61616 \
  -p 8161:8161 \
  vromero/activemq-artemis
```

- `61616`: Core messaging port
- `8161`: Web UI access → http://localhost:8161 (login: `admin` / `admin`)

---

## 🧹 Cleanup

To stop and remove the containers:
```bash
docker rm -f mongodb artemis
```

---

## ✅ Application Configuration Overview

```properties
spring.application.name=MessageNodeExecutor
server.port=7074

# Upload directory
upload.strategy=server
upload.directory=/Users/vishalgupta/Documents/json-engine/MessageManagement/UploadFile

# Messaging backend
messaging.mode=activemq

# Kafka settings (optional if using only ActiveMQ)
spring.kafka.bootstrap-servers=localhost:9092

# Queue and eviction configs
email.queue.name=email-queue
email.cache.eviction=email-config-eviction
storage.cache.eviction=storage-config-eviction

# Circuit breaker configs (Resilience4j)
resilience4j.retry.instances.kafka-retry.max-attempts=2
resilience4j.retry.instances.kafka-retry.wait-duration=500

resilience4j.circuitbreaker.instances.kafka-cb.sliding-window-size=5
resilience4j.circuitbreaker.instances.kafka-cb.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.kafka-cb.wait-duration-in-open-state=5s
```

---

## 📂 Project Folder Structure

```
MessageNodeExecutor/
├── src/
├── README.md
├── application.properties
├── Docker (optional)
└── UploadFile/
```

---

## 🛠️ Useful Links

- [MongoDB Docker Hub](https://hub.docker.com/_/mongo)
- [ActiveMQ Artemis Docker Hub](https://hub.docker.com/r/vromero/activemq-artemis)

---

Happy coding! 🚀

