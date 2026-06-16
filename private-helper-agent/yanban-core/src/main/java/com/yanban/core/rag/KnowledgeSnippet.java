package com.yanban.core.rag;

public record KnowledgeSnippet(
        Long documentId,
        String filename,
        Integer chunkIndex,
        String content,
        double score
) {
}
