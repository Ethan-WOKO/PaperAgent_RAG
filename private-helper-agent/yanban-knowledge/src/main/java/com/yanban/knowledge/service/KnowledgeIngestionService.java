package com.yanban.knowledge.service;

import com.yanban.knowledge.domain.KbChunk;
import com.yanban.knowledge.domain.KbChunkRepository;
import com.yanban.knowledge.domain.KbDocument;
import com.yanban.knowledge.domain.KbDocumentRepository;
import java.io.InputStream;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeIngestionService {

    private final KbDocumentRepository documents;
    private final KbChunkRepository chunks;
    private final FileProcessingService fileProcessingService;
    private final Tika tika = new Tika();

    public KnowledgeIngestionService(KbDocumentRepository documents,
                                     KbChunkRepository chunks,
                                     FileProcessingService fileProcessingService) {
        this.documents = documents;
        this.chunks = chunks;
        this.fileProcessingService = fileProcessingService;
    }

    @Transactional
    public KbDocument ingestSimple(Long userId, MultipartFile file, boolean isPublic) {
        try {
            KbDocument document = documents.save(new KbDocument(
                    userId,
                    file.getOriginalFilename() == null ? "uploaded-file" : file.getOriginalFilename(),
                    "PROCESSING",
                    isPublic
            ));
            String text;
            try (InputStream in = file.getInputStream()) {
                text = tika.parseToString(in);
            }
            List<KbChunk> createdChunks = fileProcessingService.splitText(document.getId(), text);
            chunks.saveAll(createdChunks);
            document.setStatus("READY");
            return documents.save(document);
        } catch (Exception ex) {
            throw new IllegalStateException("文件解析失败", ex);
        }
    }
}
