package com.yanban.knowledge.service;

import com.yanban.knowledge.domain.KbDocument;
import com.yanban.knowledge.domain.KbDocumentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Primary
@Service
public class HybridKnowledgeSearchService implements KnowledgeSearchService {

    private final EmbeddingClient embeddingClient;
    private final KnowledgeSearchIndexClient indexClient;
    private final KbDocumentRepository documents;
    private final SimpleKnowledgeSearchService fallbackSearchService;

    public HybridKnowledgeSearchService(EmbeddingClient embeddingClient,
                                        KnowledgeSearchIndexClient indexClient,
                                        KbDocumentRepository documents,
                                        SimpleKnowledgeSearchService fallbackSearchService) {
        this.embeddingClient = embeddingClient;
        this.indexClient = indexClient;
        this.documents = documents;
        this.fallbackSearchService = fallbackSearchService;
    }

    @Override
    public List<KnowledgeSearchResult> search(String query, Long userId, int topK) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        try {
            List<Double> queryVector = embeddingClient.embed(query.trim());
            List<KnowledgeSearchIndexHit> hits = indexClient.search(query.trim(), userId, topK, queryVector);
            if (hits.isEmpty()) {
                return fallbackSearchService.search(query, userId, topK);
            }
            return toResults(query.trim(), hits, topK);
        } catch (Exception ex) {
            return fallbackSearchService.search(query, userId, topK);
        }
    }

    private List<KnowledgeSearchResult> toResults(String query, List<KnowledgeSearchIndexHit> hits, int topK) {
        List<KnowledgeSearchResult> results = new ArrayList<>();
        for (KnowledgeSearchIndexHit hit : hits) {
            KbDocument document = documents.findById(hit.documentId()).orElse(null);
            if (document == null) {
                continue;
            }
            double lexicalBonus = lexicalBonus(hit.chunkText(), query);
            results.add(new KnowledgeSearchResult(
                    document.getId(),
                    document.getFilename(),
                    hit.chunkIndex(),
                    hit.chunkText(),
                    hit.vectorScore() + lexicalBonus,
                    Boolean.TRUE.equals(document.getIsPublic())
            ));
        }
        results.sort(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed());
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    private double lexicalBonus(String text, String query) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String keyword = query.toLowerCase(Locale.ROOT);
        int count = 0;
        int from = 0;
        while ((from = lower.indexOf(keyword, from)) >= 0) {
            count++;
            from += keyword.length();
        }
        return count * 0.1d;
    }
}
