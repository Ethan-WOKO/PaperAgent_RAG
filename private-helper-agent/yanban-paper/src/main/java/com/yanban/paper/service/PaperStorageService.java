package com.yanban.paper.service;

import com.yanban.paper.config.PaperStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PaperStorageService {

    private final ObjectProvider<MinioClient> minioClientProvider;
    private final PaperStorageProperties properties;

    public PaperStorageService(ObjectProvider<MinioClient> minioClientProvider,
                               PaperStorageProperties properties) {
        this.minioClientProvider = minioClientProvider;
        this.properties = properties;
    }

    public String storeOriginal(Long userId, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String safeFilename = Optional.ofNullable(file.getOriginalFilename())
                    .orElse("paper.docx")
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            String objectKey = buildObjectKey(userId, "original", safeFilename);
            storeBytes(objectKey, bytes, resolveDocxContentType(file.getContentType()));
            return objectKey;
        } catch (Exception ex) {
            throw new IllegalStateException("保存论文原始文件失败", ex);
        }
    }

    public String storeFinal(Long userId, String sourceFilename, byte[] bytes) {
        try {
            String safeFilename = Optional.ofNullable(sourceFilename)
                    .orElse("paper.docx")
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            String objectKey = buildObjectKey(userId, "final", safeFilename);
            storeBytes(objectKey, bytes, resolveDocxContentType(null));
            return objectKey;
        } catch (Exception ex) {
            throw new IllegalStateException("保存论文结果文件失败", ex);
        }
    }

    public byte[] read(String objectKey) {
        try {
            if (properties.isPreferMinio()) {
                MinioClient minioClient = minioClientProvider.getIfAvailable();
                if (minioClient != null) {
                    try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .build())) {
                        return inputStream.readAllBytes();
                    }
                }
            }
            return Files.readAllBytes(properties.getLocalRoot().resolve(objectKey));
        } catch (Exception ex) {
            throw new IllegalStateException("读取论文文件失败", ex);
        }
    }

    private void storeBytes(String objectKey, byte[] bytes, String contentType) throws Exception {
        if (properties.isPreferMinio()) {
            MinioClient minioClient = minioClientProvider.getIfAvailable();
            if (minioClient != null) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(contentType)
                        .build());
                return;
            }
        }
        Path target = properties.getLocalRoot().resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private String buildObjectKey(Long userId, String kind, String safeFilename) {
        return properties.getObjectPrefix() + "/" + userId + "/" + kind + "/" + UUID.randomUUID() + "-" + safeFilename;
    }

    private String resolveDocxContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : contentType;
    }
}
