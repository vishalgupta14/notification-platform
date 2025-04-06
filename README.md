# 📬 Notification Platform

A scalable, modular email notification system supporting snapshot-based delivery, dynamic placeholders, queue-based messaging, and pluggable storage.

---

## ✅ Completed Features

### 1. Modular Design and Snapshot Strategy
- **Snapshot model (`NotificationPayloadDTO`)** introduced to send full snapshot of `TemplateEntity` and `NotificationConfig`.
- Avoids stale data issues by freezing configuration at the time of send.

### 2. CDN Integration for Large HTML
- Added `HtmlCdnUploader` service to:
    - Upload large HTML content to a CDN server.
    - Retrieve HTML content using `fetchFromCdn()` if needed at send-time.
- Integrated **Spring Boot** based CDN server with endpoints:
    - `POST /cdn/upload` – to upload files
    - `GET /cdn/{filename}` – to fetch files
    - `DELETE /cdn/delete/{filename}` – to delete files

### 3. Email Sending Improvements
- Enhanced `EmailSendService` to:
    - Support CDN-based HTML loading if `content` is null.
    - Support fallback and privacy fallback configurations.
    - Retry mechanism for attachment download removed (based on config).
    - Partial attachment mode (`allowPartialAttachment`) supported.
- Introduced logging for all failure cases: email or attachment.

### 4. Dynamic Placeholder Resolution
- Used `TemplateUtil.resolveTemplateWithParams(...)` to:
    - Replace placeholders (`{{name}}`, `{{date}}`, etc.) in:
        - Subject
        - HTML Content
- Done **before** CDN upload to avoid resolving at runtime.

### 5. Message Queue Integration
- Support for **ActiveMQ**, **Kafka**, or **both**, using:
    - `messaging.mode` config.
- Configurable retry and circuit breaker using **Resilience4j**.
- Separate queue listener with thread pool for concurrent processing.
- Support for **pub-sub** via `setPubSubDomain(true)` for eviction queue.

### 6. File Storage Support
- Unified `FileUploader` interface with three implementations:
    - `AwsFileUploader`
    - `AzureFileUploader`
    - `ServerFileUploader`
- Upload, download, and delete logic per storage type.
- Connection pool for **S3** and **Azure** using configuration stored in DB.
- Mapped file attachments to specific storage configs via `FileReference`.

### 7. Notification Configuration Management
- `NotificationConfig` entity stores SMTP/Provider credentials.
- Created `EmailConnectionPoolManager` with **Caffeine cache** for pooling SMTP connections.
- Added **eviction queue** for refreshing connection pool.

### 9. Common Library (Fat JAR)
- Created a shared `notification-common` module:
    - Contains DTOs, upload interfaces, template utilities, etc.
- Made it a **fat jar** using **Maven Shade plugin**.
- Used in other modules like `email-sender-service` and `message-node-executor`.

### 10. Validation and Clean Coding
- All DTOs properly validated using `@NotBlank`, `@Email`, etc.
- Logs placed for every major step for better observability.
- Configurable max inline HTML size (e.g. 100 KB) using:
    - `notification.max.inline.kb=100`

---

# 📬 Notification Platform

A modular, scalable, and queue-based notification platform that supports sending **emails with rich HTML templates and attachments**, powered by **Kafka / ActiveMQ**, and supports **AWS, Azure, and Server-based file storage**.

---

## 📦 Modules

| Module                     | Description                                             |
|---------------------------|---------------------------------------------------------|
| `notification-common`     | Shared DTOs, utility classes, upload abstractions       |
| `message-node-executor`   | Receives API requests and dispatches to Kafka/ActiveMQ  |
| `email-sender-service`    | Consumes messages and sends email using SMTP            |
| `cdn-server`              | Lightweight CDN to host and serve large HTML templates  |

---

## 🚀 Features

### ✅ General

- Snapshot-based email delivery (config + template frozen at send time)
- Supports both **ActiveMQ** and **Kafka**
- Supports **cron-based scheduled notifications**
- Fully **Spring Boot** based microservices
- Fat JAR for shared common utilities

### ✉️ Email

- Dynamic subject & HTML using `{{placeholders}}`
- Fallback & privacy fallback SMTP configurations
- HTML size threshold (default: 100KB) configurable
- Sends email with **CC / BCC / multiple recipients**
- Configurable retry & circuit-breakers using Resilience4j

### 📂 File Storage

- Pluggable file upload support
- Uploads & downloads supported for:
    - ✅ AWS S3
    - ✅ Azure Blob
    - ✅ Local server
- Attachments fetched from correct storage at runtime

### 🌐 CDN Server

- Upload large HTML files
- Serve using `/cdn/{filename}`
- Integrated with notification system
- Used when HTML content is too large for MQ message

---

## ⚙️ Configuration

### ✅ `application.properties` example

```properties
# Mongo
spring.data.mongodb.uri=mongodb://localhost:27017/hyperflow

# Port
server.port=8112

# Upload Strategy
upload.strategy=server
upload.directory=/path/to/local/upload

# Messaging Mode
messaging.mode=activemq  # Options: kafka | activemq | both

# ActiveMQ
spring.artemis.mode=native
spring.artemis.host=localhost
spring.artemis.port=61616
spring.artemis.user=admin
spring.artemis.password=admin

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# Queues
email.queue.name=email-queue
email.cache.eviction=email-config-eviction
storage.cache.eviction=storage-config-eviction

# CDN
cdn.base-url=http://localhost:8111/cdn
notification.max.inline.kb=100

# Email Control
notification.email.enabled=true
notification.email.allowPartialAttachment=true
```

## 🏗️ Tech Stack

```Layer	Technology
📦 Backend	Spring Boot 3.4.x
🗃️ Database	MongoDB
📨 Messaging	Kafka / ActiveMQ
✉️ Mail Client	JavaMailSender + SMTP
♻️ Resilience	Resilience4j
⚡ Cache	Caffeine Cache
🌐 CDN Server	Spring Boot (standalone)
📦 Build Tool	Maven + Shade Plugin
```

# 📬 Notification System – REST API Guide

This document contains a complete guide for interacting with the Notification System using cURL.

---

## 🔧 Prerequisites

- Make sure your Spring Boot app is running on `http://localhost:8112`
- MongoDB is up and accessible at `mongodb://localhost:27017/hyperflow`
- Artemis broker is running (if messaging mode is set to `activemq`)
- Upload directory exists on local server

---

## 1️⃣ Create File Storage Config (e.g., local server)

```bash
curl -X POST http://localhost:8112/api/storage-config \
  -H "Content-Type: application/json" \
  -d '{
        "fileStorageName": "server-local",
        "type": "server",
        "properties": {
            "uploadDir": "/Users/your-user/Documents/your-path/UploadFile"
        },
        "description": "Local file server storage",
        "isActive": true
      }'
```

---

## 2️⃣ Get All File Storage Configs

```bash
curl -X GET http://localhost:8112/api/storage-config/all
```

---

## 3️⃣ Upload File Using File Storage Config

```bash
curl -X POST http://localhost:8112/api/template/upload \
  -F "fileStorageId=REPLACE_WITH_STORAGE_ID" \
  -F "files=@/Users/your-user/Downloads/sample.pdf"
```

Response Example:
```json
[
  "http://localhost:8112/UploadFile/abc123-sample.pdf"
]
```

---

## 4️⃣ Save Email Template

```bash
curl -X POST http://localhost:8112/api/template/save \
  -H "Content-Type: application/json" \
  -d '{
        "templateName": "Invoice Template",
        "emailSubject": "Your Invoice is Ready",
        "content": "<h2>Please find your invoice attached.</h2>",
        "cdnUrl": "",
        "attachments": [
          {
            "fileUrl": "http://localhost:8112/UploadFile/abc123-sample.pdf",
            "storageType": "server",
            "fileStorageId": "REPLACE_WITH_STORAGE_ID"
          }
        ],
        "createdBy": "system-user",
        "createdAt": "2025-04-05T12:00:00"
      }'
```

---

## 5️⃣ Get All Email Templates

```bash
curl -X GET http://localhost:8112/api/template/all
```

---

## 6️⃣ Save SMTP Notification Config

```bash
curl -X POST http://localhost:8112/api/config \
  -H "Content-Type: application/json" \
  -d '{
        "clientName": "client-email",
        "channel": "email",
        "provider": "smtp",
        "config": {
          "host": "smtp.example.com",
          "port": 587,
          "username": "user@example.com",
          "password": "your_password_here"
        },
        "fallbackConfigId": null,
        "privacyFallbackConfig": {},
        "isActive": true
      }'
```

---

## 7️⃣ Get All Notification Configs

```bash
curl -X GET http://localhost:8112/api/config/all
```

---

## 8️⃣ Send Email Using Config + Template

```bash
curl -X POST http://localhost:8112/api/config/send-email \
  -H "Content-Type: application/json" \
  -d '{
    "notificationConfigId": "REPLACE_WITH_CONFIG_ID",
    "templateId": "REPLACE_WITH_TEMPLATE_ID",
    "to": "recipient@example.com",
    "emailSubject": "Invoice Email Test",
    "customParams": {
      "customerName": "Sample Name",
      "invoiceDate": "2025-04-05"
    },
    "scheduled": false
  }'
```

---

## ⚙️ Performance

- **4KB HTML email**
- Average **send time** per email: **4000 ms (4 seconds)**

---

### 🧠 Notes

- Don't forget to set correct `fileStorageId` and `templateId` in the POST requests
- SMTP password should be securely encrypted or use an app password if using Gmail
- Upload files < 20 MB directly; large files should be CDN-hosted

---

### 🛠️ Configuration

Make sure you configure the following in your `application.properties`:

```properties
server.port=8112
upload.directory=/Users/your-user/Documents/your-path/UploadFile
messaging.mode=activemq
email.queue.name=email-queue
```


## 📲 SMS Notification Service - Usage Guide

This guide demonstrates how to work with the SMS Notification APIs:

- ✅ Save templates for SMS
- 🔐 Configure providers like Twilio
- 📤 Send SMS via ActiveMQ/Kafka

---

### 1️⃣ Save SMS Template

```bash
curl -X POST http://localhost:8112/api/template/save \
  -H "Content-Type: application/json" \
  -d '{
        "templateName": "Invoice Template for SMS",
        "content": "Hi John, your invoice for $250 is ready. Please check your email.",
        "createdBy": "vishal.gupta",
        "createdAt": "2025-04-05T12:00:00"
      }'
```

---

### 2️⃣ Get All Templates

```bash
curl -X GET http://localhost:8112/api/template/all
```

---

### 3️⃣ Save Twilio SMS Config

```bash
curl -X POST http://localhost:8112/api/config \
  -H "Content-Type: application/json" \
  -d '{
        "clientName": "twilio-sms-client",
        "channel": "sms",
        "provider": "twilio",
        "config": {
          "provider": "twilio",
          "accountSid": "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
          "authToken": "xxxxxxxxxxxxxxxxxxxxxx",
          "from": "+1989"
        },
        "fallbackConfigId": null,
        "isActive": true
      }'
```

---

### 4️⃣ Get All Configs

```bash
curl -X GET http://localhost:8112/api/config/all
```

---

### 5️⃣ Send SMS Notification

```bash
curl -X POST http://localhost:8112/api/config/send-sms \
  -H "Content-Type: application/json" \
  -d '{
    "notificationConfigId": "67f23dd727d79e19634a69cf",
    "templateId": "67f23dbf27d79e19634a69ce",
    "to": "+91212",
    "customParams": {
      "customerName": "Vishal Gupta",
      "orderId": "ORD-98765",
      "otp": "123456"
    },
    "scheduled": false
  }'
```

---

### 🧠 Notes

- 🔁 `customParams` are injected into the template using `{{key}}` syntax.
- ⏱️ `scheduled: false` = Send immediately.
- 🔐 Avoid hardcoding real tokens/passwords. Use secrets/vaults.
- 📌 Replace placeholder IDs (`notificationConfigId`, `templateId`) with actual ones from your DB.

---

### ✅ Sample Template with Placeholders

```
Hi {{customerName}}, your order {{orderId}} is confirmed. Use OTP: {{otp}}.
```


---

For any questions, contact the system maintainer. 🛠️

## 📄 License

MIT
