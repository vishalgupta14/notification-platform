package com.notification.common.service.upload;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component("azure")
public class AzureFileUploader implements FileUploader {

    @Override
    public List<String> uploadFiles(MultipartFile[] files, Map<String, Object> properties) {
        String connectionString = properties.get("connectionString").toString();
        String containerName = properties.get("container").toString();

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);

        List<String> uploadedUrls = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String blobName = UUID.randomUUID() + "-" + Objects.requireNonNull(file.getOriginalFilename());

                BlobClient blobClient = containerClient.getBlobClient(blobName);
                blobClient.upload(file.getInputStream(), file.getSize(), true);

                uploadedUrls.add(blobClient.getBlobUrl());

            } catch (IOException e) {
                throw new RuntimeException("Failed to upload file to Azure Blob Storage", e);
            }
        }

        return uploadedUrls;
    }

    @Override
    public String getFileUrl(String blobName, Map<String, Object> properties) {
        String connectionString = properties.get("connectionString").toString();
        String containerName = properties.get("container").toString();

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        return blobClient.getBlobUrl();
    }

    @Override
    public void deleteFile(String blobName, Map<String, Object> properties) {
        String connectionString = properties.get("connectionString").toString();
        String containerName = properties.get("container").toString();

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        blobClient.deleteIfExists();
    }

    @Override
    public InputStream downloadFile(String blobName, Map<String, Object> properties) throws IOException {
        String connectionString = properties.get("connectionString").toString();
        String containerName = properties.get("container").toString();

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        if (!blobClient.exists()) {
            throw new IOException("Blob not found: " + blobName);
        }

        return blobClient.openInputStream();
    }
}
