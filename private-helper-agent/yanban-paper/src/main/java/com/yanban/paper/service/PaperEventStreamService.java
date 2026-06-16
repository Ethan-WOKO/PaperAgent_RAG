package com.yanban.paper.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class PaperEventStreamService {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<Long, List<PaperSseEvent>> history = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(taskId, key -> new ArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(ex -> removeEmitter(taskId, emitter));
        history.getOrDefault(taskId, List.of()).forEach(event -> sendEvent(emitter, event));
        return emitter;
    }

    public void publish(PaperSseEvent event) {
        history.computeIfAbsent(event.taskId(), key -> new ArrayList<>()).add(event);
        for (SseEmitter emitter : new ArrayList<>(emitters.getOrDefault(event.taskId(), List.of()))) {
            sendEvent(emitter, event);
        }
    }

    private void sendEvent(SseEmitter emitter, PaperSseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event));
        } catch (IOException ex) {
            emitter.complete();
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        emitters.computeIfPresent(taskId, (key, values) -> {
            values.remove(emitter);
            return values.isEmpty() ? null : values;
        });
    }
}
