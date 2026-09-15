package com.the.weather.storage.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.the.weather.post.exception.PostApiException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3MediaService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final long maximumBytes;
    private final Duration signedUrlLifetime;

    public S3MediaService(
            S3Client s3,
            S3Presigner presigner,
            @Value("${storage.post-images-bucket}") String bucket,
            @Value("${storage.post-image-max-bytes}") long maximumBytes,
            @Value("${storage.presigned-get-minutes}") long signedUrlMinutes) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
        this.maximumBytes = maximumBytes;
        this.signedUrlLifetime = Duration.ofMinutes(signedUrlMinutes);
    }

    public void validateImages(List<MultipartFile> images) {
        if (images.size() > 3) {
            throw new PostApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_IMAGES",
                    "A community post can contain a maximum of 3 images.");
        }
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty() || !EXTENSIONS.containsKey(image.getContentType())) {
                throw new PostApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_TYPE",
                        "Images must be non-empty JPEG, PNG, or WebP files.");
            }
            if (image.getSize() > maximumBytes) {
                throw new PostApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE",
                        "Each community image must be 5 MB or smaller.");
            }
        }
        if (!images.isEmpty() && !StringUtils.hasText(bucket)) {
            throw new PostApiException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_STORAGE_UNAVAILABLE",
                    "Community image storage is not configured.");
        }
    }

    public String upload(String userID, String postID, MultipartFile image) {
        String extension = EXTENSIONS.get(image.getContentType());
        String key = "community-posts/%s/%s/%s.%s".formatted(
                userID, postID, UUID.randomUUID(), extension);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key).contentType(image.getContentType()).build(),
                    RequestBody.fromInputStream(image.getInputStream(), image.getSize()));
            return key;
        } catch (IOException exception) {
            throw new PostApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_UPLOAD_FAILED",
                    "A community image could not be uploaded.");
        }
    }

    public List<String> createReadUrls(List<String> keys) {
        if (keys.isEmpty()) return List.of();
        if (!StringUtils.hasText(bucket)) return List.of();
        return keys.stream().map(this::createReadUrl).toList();
    }

    public void deleteAll(List<String> keys) {
        if (!StringUtils.hasText(bucket)) return;
        for (String key : keys) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }
    }

    private String createReadUrl(String key) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(signedUrlLifetime).getObjectRequest(get).build())
                .url().toString();
    }
}
