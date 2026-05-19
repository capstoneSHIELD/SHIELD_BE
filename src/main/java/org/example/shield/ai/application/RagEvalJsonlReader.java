package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.RagEvalItem;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class RagEvalJsonlReader {

    private final ObjectMapper objectMapper;

    public RagEvalJsonlReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RagEvalItem> read(Path path) {
        try {
            return read(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read RAG eval JSONL: " + path, e);
        }
    }

    public List<RagEvalItem> read(String jsonl) {
        if (jsonl == null || jsonl.isBlank()) {
            return List.of();
        }
        List<RagEvalItem> items = new ArrayList<>();
        String[] lines = jsonl.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                items.add(objectMapper.readValue(line, RagEvalItem.class));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid RAG eval JSONL at line " + (i + 1), e);
            }
        }
        return List.copyOf(items);
    }
}
