package com.yanban.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yanban.knowledge.domain.KbDocument;
import com.yanban.knowledge.domain.KbDocumentRepository;
import com.yanban.knowledge.domain.KbChunkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;

class HybridKnowledgeSearchServiceTest {

    @Test
    void searchUsesHybridIndexResultsWhenAvailable() {
        EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
        KnowledgeSearchIndexClient indexClient = Mockito.mock(KnowledgeSearchIndexClient.class);
        KbDocumentRepository documents = Mockito.mock(KbDocumentRepository.class);
        KbChunkRepository chunks = Mockito.mock(KbChunkRepository.class);
        SimpleKnowledgeSearchService fallback = new SimpleKnowledgeSearchService(chunks, documents);
        HybridKnowledgeSearchService service = new HybridKnowledgeSearchService(embeddingClient, indexClient, documents, fallback);

        when(embeddingClient.embed("alpha")).thenReturn(List.of(0.1d, 0.2d));
        when(indexClient.search("alpha", 1001L, 3, List.of(0.1d, 0.2d))).thenReturn(List.of(
                new KnowledgeSearchIndexHit(1L, 0, "alpha content", 1.5d)
        ));
        when(documents.findById(1L)).thenReturn(java.util.Optional.of(new KbDocument(1001L, "paper.md", "READY", false)));

        List<KnowledgeSearchResult> results = service.search("alpha", 1001L, 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).filename()).isEqualTo("paper.md");
        assertThat(results.get(0).score()).isGreaterThan(1.5d);
    }

    @Test
    void searchFallsBackToDatabaseWhenHybridFails() {
        EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
        KnowledgeSearchIndexClient indexClient = Mockito.mock(KnowledgeSearchIndexClient.class);
        KbDocumentRepository documents = Mockito.mock(KbDocumentRepository.class);
        KbChunkRepository chunks = Mockito.mock(KbChunkRepository.class);
        SimpleKnowledgeSearchService fallback = new SimpleKnowledgeSearchService(chunks, documents);
        HybridKnowledgeSearchService service = new HybridKnowledgeSearchService(embeddingClient, indexClient, documents, fallback);

        when(embeddingClient.embed("beta")).thenThrow(new IllegalStateException("embedding down"));
        com.yanban.knowledge.domain.KbChunk chunk = new com.yanban.knowledge.domain.KbChunk(1L, 0, "beta keyword");
        when(chunks.searchAccessibleChunks("beta", 2002L, PageRequest.of(0, 2))).thenReturn(List.of(chunk));
        when(documents.findById(1L)).thenReturn(java.util.Optional.of(new KbDocument(2002L, "notes.md", "READY", false)));

        List<KnowledgeSearchResult> results = service.search("beta", 2002L, 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).filename()).isEqualTo("notes.md");
    }
}
