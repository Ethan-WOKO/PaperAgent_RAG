package com.yanban.knowledge.web;

import com.yanban.knowledge.domain.KbDocument;
import java.time.Instant;

public record KbDocumentResponse(
        Long id,
        Long userId,
        String filename,
        String status,
        boolean isPublic,
        Instant createdAt,
        Instant updatedAt
) {
    public static KbDocumentResponse from(KbDocument document) {
        return new KbDocumentResponse(
                document.getId(),
                document.getUserId(),
                document.getFilename(),
                document.getStatus(),
                Boolean.TRUE.equals(document.getIsPublic()),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
