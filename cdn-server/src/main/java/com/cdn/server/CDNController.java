package com.cdn.server;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/cdn")
public class CDNController {

    private static final Logger log = LoggerFactory.getLogger(CDNController.class);

    @Value("${upload.directory}")
    private String uploadDir;

    private Path basePath;

    @PostConstruct
    public void init() throws IOException {
        this.basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(basePath);
        log.info("CDN initialized at: {}", basePath);
    }

    /**
     * Upload any file (HTML, image, PDF, etc.)
     */
    @PostMapping("/upload")
    public ResponseEntity<List<String>> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        List<String> fileUrls = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = timestamp + "-" + UUID.randomUUID() + "-" + originalName;


            try {
                Path targetPath = basePath.resolve(filename);
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/cdn/")
                        .path(filename)
                        .toUriString();

                fileUrls.add(fileUrl);
                log.info("File uploaded: {}", fileUrl);

            } catch (IOException e) {
                log.error("Failed to upload file {}", originalName, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Collections.singletonList("Failed to upload: " + originalName));
            }
        }

        return ResponseEntity.ok(fileUrls);
    }

    /**
     * Serve any uploaded file via CDN
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        try {
            Path filePath = basePath.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) return ResponseEntity.notFound().build();

            MediaType contentType = detectContentType(filePath);
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Invalid file path: {}", filename, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete file
     */
    @DeleteMapping("/delete/{filename:.+}")
    public ResponseEntity<String> deleteByFilename(@PathVariable String filename) {
        try {
            Path filePath = basePath.resolve(filename);

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Deleted file: {}", filename);
                return ResponseEntity.ok("Deleted: " + filename);
            } else {
                log.warn("File not found: {}", filename);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found: " + filename);
            }

        } catch (Exception e) {
            log.error("Error deleting file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting file: " + e.getMessage());
        }
    }


    private MediaType detectContentType(Path path) {
        try {
            String mime = Files.probeContentType(path);
            return mime != null ? MediaType.parseMediaType(mime) : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
