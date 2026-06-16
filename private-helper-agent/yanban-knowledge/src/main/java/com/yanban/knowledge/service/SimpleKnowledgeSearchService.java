package com.yanban.knowledge.service;

import com.yanban.knowledge.domain.KbChunk;
import com.yanban.knowledge.domain.KbChunkRepository;
import com.yanban.knowledge.domain.KbDocument;
import com.yanban.knowledge.domain.KbDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service("databaseFallbackKnowledgeSearchService")
public class SimpleKnowledgeSearchService implements KnowledgeSearchService {

    private final KbChunkRepository chunks;
    private final KbDocumentRepository documents;

    public SimpleKnowledgeSearchService(KbChunkRepository chunks, KbDocumentRepository documents) {
        this.chunks = chunks;
        this.documents = documents;
    }

    @Override
    public List<KnowledgeSearchResult> search(String query, Long userId, int topK) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        List<KbChunk> found = chunks.searchAccessibleChunks(query.trim(), userId, PageRequest.of(0, topK));
        List<KnowledgeSearchResult> results = new ArrayList<>();
        String keyword = query.toLowerCase(Locale.ROOT);
        for (KbChunk chunk : found) {
            KbDocument document = documents.findById(chunk.getDocumentId()).orElse(null);
            if (document == null) {
                continue;
            }
            results.add(new KnowledgeSearchResult(
                    document.getId(),
                    document.getFilename(),
                    chunk.getChunkIndex(),
                    chunk.getChunkText(),
                    score(chunk.getChunkText(), keyword),
                    Boolean.TRUE.equals(document.getIsPublic())
            ));
        }
        return results;
    }

    private double score(String text, String keyword) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int count = 0;
        int from = 0;
        while ((from = lower.indexOf(keyword, from)) >= 0) {
            count++;
            from += keyword.length();
        }
        return Math.max(1.0, count);
    }
}
