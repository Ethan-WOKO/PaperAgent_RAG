package com.yanban.knowledge.web;

import com.yanban.knowledge.domain.KbDocument;
import java.time.Instant;

public record KbDocumentListItemResponse(
        Long id,
        Long userId,
        String filename,
        String status,
        boolean isPublic,
        String mimeType,
        Long fileSize,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static KbDocumentListItemResponse from(KbDocument document) {
        return new KbDocumentListItemResponse(
                document.getId(),
                document.getUserId(),
                document.getFilename(),
                document.getStatus(),
                Boolean.TRUE.equals(document.getIsPublic()),
                document.getMimeType(),
                document.getFileSize(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
