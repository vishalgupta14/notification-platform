# 📬 Notification Config Service

A Spring Boot microservice to manage and store notification configurations in MongoDB for different clients and channels (Email, SMS, WhatsApp, Push, etc.).

---

## 🛠️ Tech Stack

- Java 8+
- Spring Boot
- Spring Data MongoDB
- MongoDB (Local or Cloud)
- REST API (JSON-based)

---

## ⚙️ MongoDB Setup

Make sure MongoDB is running locally:

```bash
mongod --dbpath /path/to/your/db
```

If you're using cloud MongoDB, update your `application.properties`:

```
spring.data.mongodb.uri=mongodb://localhost:27017/hyperflow
```

---

## 🚀 How to Run

```bash
./mvnw spring-boot:run
```

Or if using Maven directly:

```bash
mvn spring-boot:run
```

---

# 📡 Notification Config API

This Spring Boot controller provides REST endpoints to manage **Notification Channel Configurations** (e.g., for Email, SMS, WhatsApp, etc.) with secure handling of sensitive fields like passwords and API tokens.

---

## 🔧 Features

- ✅ Create new notification configurations
- ✅ Fetch configuration (with sensitive fields masked) for UI display
- ✅ Update existing configuration
- ✅ Delete configuration by ID
- ✅ Fetch all configurations in UI-safe format

---

## 📁 Base Path

```
/api/config
```

---

## 📘 Endpoints

### ➕ `POST /api/config`

Create a new configuration.

#### Request Body

```json
{
  "clientId": "client-a",
  "channel": "EMAIL",
  "provider": "SMTP",
  "config": {
    "host": "smtp.gmail.com",
    "port": 587,
    "username": "noreply@clienta.com",
    "password": "super-secret-password",
    "from": "Client A <noreply@clienta.com>",
    "secure": true
  }
}
```

#### cURL

```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
        "clientId": "client-a",
        "channel": "EMAIL",
        "provider": "SMTP",
        "config": {
          "host": "smtp.gmail.com",
          "port": 587,
          "username": "noreply@clienta.com",
          "password": "super-secret-password",
          "from": "Client A <noreply@clienta.com>",
          "secure": true
        }
      }'
```

---

### 👁️ `GET /api/config/ui/{clientId}/{channel}`

Fetch configuration by client and channel for UI display (sensitive fields are masked).

#### Response Example

```json
{
  "id": "6611fa4f9cbbf345e1ab85c4",
  "clientId": "client-a",
  "channel": "EMAIL",
  "provider": "SMTP",
  "configSummary": {
    "host": "smtp.gmail.com",
    "from": "noreply@clienta.com",
    "username": "noreply@clienta.com",
    "password": "************8fhJ"
  },
  "active": true,
  "updatedAt": "2025-03-31T18:20:00"
}
```

#### cURL

```bash
curl http://localhost:8080/api/config/ui/client-a/EMAIL
```

---

### ♻️ `PUT /api/config/{id}`

Update an existing configuration. If sensitive fields are sent as `"********"`, original values are retained.

#### Request Body

```json
{
  "clientId": "client-a",
  "channel": "EMAIL",
  "provider": "SMTP",
  "config": {
    "host": "smtp.gmail.com",
    "password": "********",
    "from": "new-sender@clienta.com"
  }
}
```

#### cURL

```bash
curl -X PUT http://localhost:8080/api/config/your-config-id \
  -H "Content-Type: application/json" \
  -d '{
        "clientId": "client-a",
        "channel": "EMAIL",
        "provider": "SMTP",
        "config": {
          "host": "smtp.gmail.com",
          "password": "********",
          "from": "new-sender@clienta.com"
        }
      }'
```

---

### ❌ `DELETE /api/config/{id}`

Delete a configuration by its ID.

#### cURL

```bash
curl -X DELETE http://localhost:8080/api/config/your-config-id
```

---

### 📋 `GET /api/config/all`

Returns a list of all notification configurations with masked sensitive fields for UI display.

#### cURL

```bash
curl http://localhost:8080/api/config/all
```

---

## 🔒 Security Notes

- Sensitive fields like `password`, `authToken`, `apiKey`, etc. are:
    - 🔐 Encrypted at rest using AES
    - 👁️ Masked before being sent to frontend
- Masked format: `************abcd`

---

## 🧩 Technologies Used

- Spring Boot
- Spring Data MongoDB
- AES Encryption
- RESTful API
- Java 8+

---

## 📦 Package Structure

```
com.message.node
├── controller
│   └── NotificationConfigController.java
├── service
│   └── NotificationConfigService.java
├── model
│   └── NotificationConfig.java
├── dto
│   └── NotificationConfigDTO.java
├── util
│   └── ConfigMaskingUtil.java
```