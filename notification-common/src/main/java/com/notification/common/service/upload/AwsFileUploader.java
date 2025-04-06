package com.notification.common.service.upload;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component("aws")
public class AwsFileUploader implements FileUploader {

    @Override
    public List<String> uploadFiles(MultipartFile[] files, Map<String, Object> properties) {
        String accessKey = properties.get("accessKey").toString();
        String secretKey = properties.get("secretKey").toString();
        String region = properties.get("region").toString();
        String bucketName = properties.get("bucket").toString();

        S3Client s3Client = buildS3Client(accessKey, secretKey, region);
        List<String> uploadedUrls = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String key = UUID.randomUUID() + "-" + Objects.requireNonNull(file.getOriginalFilename());

                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(key)
                                .contentType(file.getContentType())
                                .build(),
                        RequestBody.fromBytes(file.getBytes()));

                String fileUrl = getFileUrl(bucketName,properties);
                uploadedUrls.add(fileUrl);

            } catch (IOException e) {
                throw new RuntimeException("Failed to upload file to AWS S3: " + e.getMessage(), e);
            }
        }

        return uploadedUrls;
    }

    @Override
    public String getFileUrl(String key, Map<String, Object> properties) {
        String bucket = properties.get("bucket").toString();
        String region = properties.get("region").toString();
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
    }

    @Override
    public void deleteFile(String key, Map<String, Object> properties) {
        String accessKey = properties.get("accessKey").toString();
        String secretKey = properties.get("secretKey").toString();
        String region = properties.get("region").toString();
        String bucketName = properties.get("bucket").toString();

        S3Client s3Client = buildS3Client(accessKey, secretKey, region);

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Error deleting file from S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public InputStream downloadFile(String key, Map<String, Object> properties) {
        String accessKey = properties.get("accessKey").toString();
        String secretKey = properties.get("secretKey").toString();
        String region = properties.get("region").toString();
        String bucketName = properties.get("bucket").toString();

        S3Client s3Client = buildS3Client(accessKey, secretKey, region);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.getObject(getObjectRequest);
    }

    private S3Client buildS3Client(String accessKey, String secretKey, String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

}
