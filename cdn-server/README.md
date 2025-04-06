# 📆 CDN Server - Spring Boot

A lightweight Spring Boot-based **CDN-style file server** that allows you to:

- 📄 Upload HTML, images, PDFs, ZIPs, and any other file
- 🌐 Serve uploaded files over public URLs
- 🗑️ Delete files by filename

---

## ⚙️ Configuration (`application.properties`)
```properties
spring.application.name=cdn-server
server.port=8111
upload.directory=uploads/cdn
```

- Uploaded files will be stored in the `uploads/cdn` directory (relative to the working directory).

---

## 🚀 Running the Server

```bash
mvn spring-boot:run
```

Server will be available at:

```
http://localhost:8111
```

---

## 📄 Upload Files

**Endpoint:** `POST /cdn/upload`

**File Naming Convention:**
```
<timestamp>-<uuid>-<originalFilename>
```
Example:
```
1712327891001-8f4a9b7a-file.pdf
```

**Example cURL:**
```bash
curl -X POST http://localhost:8111/cdn/upload \
  -F "files=@/path/to/file.pdf" \
  -F "files=@/path/to/image.png"
```

**Sample Response:**
```json
[
  "http://localhost:8111/cdn/1712327891001-8f4a9b7a-file.pdf",
  "http://localhost:8111/cdn/1712327891002-2bc67c3e-image.png"
]
```

---

## 📃 Fetch File by URL

**Endpoint:** `GET /cdn/{filename}`

**Example:**
```
http://localhost:8111/cdn/1712327891001-8f4a9b7a-file.pdf
```

---

## ❌ Delete File by Filename

**Endpoint:** `DELETE /cdn/delete/{filename}`

**Example cURL:**
```bash
curl -X DELETE "http://localhost:8111/cdn/delete/1712327891001-8f4a9b7a-file.pdf"
```

**Response:**
```
Deleted: 1712327891001-8f4a9b7a-file.pdf
```

---

## 🎉 Notes
- All files are renamed using a combination of **timestamp + UUID + original filename**
- Files of all types are supported (binary, text, media, etc.)
- `Content-Type` is auto-detected and returned correctly
- Public URLs are shareable and can be embedded in templates, systems, or messages

---

## 🚀 Future Enhancements
- Authentication/token-based access for uploads and deletes
- Auto-expiry of old files
- Support for preview or directory listing
- Pluggable storage backend (e.g., AWS S3, Azure Blob, etc.)

