package com.yanban.knowledge.service;

public record KnowledgeSearchResult(
        Long documentId,
        String filename,
        Integer chunkIndex,
        String chunkText,
        double score,
        boolean isPublic
) {
}
