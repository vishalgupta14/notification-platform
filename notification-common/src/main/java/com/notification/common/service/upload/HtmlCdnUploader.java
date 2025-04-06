package com.notification.common.service.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HtmlCdnUploader {

    @Value("${cdn.base-url}")
    private String cdnBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Uploads the given HTML content as a temporary file to CDN.
     */
    public String uploadHtmlAsFile(String htmlContent) throws IOException {
        File tempFile = File.createTempFile("template", ".html");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(htmlContent);
        }

        org.springframework.core.io.FileSystemResource resource = new org.springframework.core.io.FileSystemResource(tempFile);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", resource);

        List<String> result = restTemplate.postForObject(
                cdnBaseUrl + "/upload",
                new HttpEntity<>(body),
                List.class
        );

        return result != null && !result.isEmpty() ? result.get(0) : null;
    }

    /**
     * Downloads HTML content from the given CDN URL.
     */
    public String fetchFromCdn(String cdnUrl) {
        try {
            String filename = extractFilename(cdnUrl);
            String fullCdnDownloadUrl = cdnBaseUrl + "/" + filename;

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.ALL));

            ResponseEntity<String> response = restTemplate.exchange(
                    fullCdnDownloadUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new RuntimeException("CDN returned non-OK response: " + response.getStatusCode());
            }

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to fetch HTML from CDN: " + cdnUrl, e);
        }
    }

    /**
     * Deletes the uploaded HTML file from CDN using the URL.
     */
    public boolean deleteFromCdn(String cdnUrl) {
        try {
            String filename = extractFilename(cdnUrl);
            String fullDeleteUrl = cdnBaseUrl + "/delete/" + filename;

            ResponseEntity<String> response = restTemplate.exchange(
                    fullDeleteUrl,
                    HttpMethod.DELETE,
                    null,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            } else {
                throw new RuntimeException("CDN delete failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to delete file from CDN: " + cdnUrl, e);
        }
    }

    /**
     * Extracts filename from a full CDN URL.
     */
    private String extractFilename(String cdnUrl) {
        if (cdnUrl == null || !cdnUrl.contains("/")) {
            throw new IllegalArgumentException("Invalid CDN URL: " + cdnUrl);
        }
        return cdnUrl.substring(cdnUrl.lastIndexOf('/') + 1);
    }
}
