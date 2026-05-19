package org.example.shield.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.OfflineQualityReportRecord;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OfflineQualityReportJob {

    private final ObjectMapper objectMapper;

    public OfflineQualityReportJob(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJsonl(List<OfflineQualityReportRecord> records) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OfflineQualityReportRecord record : records) {
            if (record == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(toJson(record));
        }
        return sb.toString();
    }

    private String toJson(OfflineQualityReportRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize offline quality report record", e);
        }
    }
}
