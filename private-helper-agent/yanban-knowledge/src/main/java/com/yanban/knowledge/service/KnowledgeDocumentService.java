package com.yanban.knowledge.service;

import com.yanban.knowledge.config.KnowledgeStorageProperties;
import com.yanban.knowledge.domain.KbChunkRepository;
import com.yanban.knowledge.domain.KbDocument;
import com.yanban.knowledge.domain.KbDocumentRepository;
import com.yanban.knowledge.web.KbDocumentListItemResponse;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeDocumentService {

    private final KbDocumentRepository documents;
    private final KbChunkRepository chunks;
    private final KnowledgeIndexService indexService;
    private final MinioClient minioClient;
    private final KnowledgeStorageProperties storageProperties;

    public KnowledgeDocumentService(KbDocumentRepository documents,
                                    KbChunkRepository chunks,
                                    KnowledgeIndexService indexService,
                                    MinioClient minioClient,
                                    KnowledgeStorageProperties storageProperties) {
        this.documents = documents;
        this.chunks = chunks;
        this.indexService = indexService;
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
    }

    @Transactional(readOnly = true)
    public List<KbDocumentListItemResponse> listOwnedDocuments(Long userId) {
        return documents.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(KbDocumentListItemResponse::from)
                .toList();
    }

    @Transactional
    public void deleteOwnedDocument(Long userId, Long documentId) {
        KbDocument document = documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库文档不存在"));
        chunks.deleteByDocumentId(documentId);
        indexService.deleteByDocumentId(documentId);
        removeObjectQuietly(document.getObjectKey());
        documents.delete(document);
    }

    private void removeObjectQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(storageProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("删除 MinIO 文档失败", ex);
        }
    }
}
