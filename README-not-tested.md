
## 💬 WhatsApp Notification Service - Usage Guide

This guide demonstrates how to work with the WhatsApp Notification APIs:

- ✅ Save templates for WhatsApp
- 🔐 Configure providers like Twilio
- 📤 Send WhatsApp messages via ActiveMQ/Kafka

---

### 1️⃣ Save WhatsApp Template

```bash
curl -X POST http://localhost:8112/api/template/save \
  -H "Content-Type: application/json" \
  -d '{
        "templateName": "Order Confirmation WhatsApp Template",
        "content": "Hi John, your order #12345 has been confirmed. Amount: $250. Delivery in 2 days.",
        "createdBy": "vishal.gupta",
        "createdAt": "2025-04-07T10:00:00"
      }'
```

---

### 2️⃣ Get All Templates

```bash
curl -X GET http://localhost:8112/api/template/all
```

---

### 3️⃣ Save Twilio WhatsApp Config

```bash
curl -X POST http://localhost:8112/api/config \
  -H "Content-Type: application/json" \
  -d '{
        "clientName": "twilio-whatsapp-client",
        "channel": "whatsapp",
        "provider": "twilio",
        "config": {
          "provider": "twilio",
          "accountSid": "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
          "authToken": "xxxxxxxxxxxxxxxxxxxxxx",
          "from": "+1415xxxxxxx"
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

### 5️⃣ Send WhatsApp Notification

```bash
curl -X POST http://localhost:8112/api/config/send-whatsapp \
  -H "Content-Type: application/json" \
  -d '{
    "notificationConfigId": "REPLACE_WITH_CONFIG_ID",
    "templateId": "REPLACE_WITH_TEMPLATE_ID",
    "to": "+919876543210",
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
- 📸 Attachments like images/videos can be added to the template definition.
- ⏱️ `scheduled: false` = Send immediately.
- 🔐 Avoid hardcoding real tokens/passwords. Use secrets/vaults.
- 📌 Replace placeholder IDs (`notificationConfigId`, `templateId`) with actual ones from your DB.

---

### ✅ Sample Template with Placeholders

```
Hi {{customerName}}, your order {{orderId}} is confirmed. Use OTP: {{otp}}.
```