package org.example.shield.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.IntentShadowEvalRecord;
import org.example.shield.ai.dto.LegalAdviceLabelRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class IntentShadowEvalExporter {

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean includeRawText;

    public IntentShadowEvalExporter(
            ObjectMapper objectMapper,
            @Value("${app.ai.intent-router.shadow-export.enabled:false}") boolean enabled,
            @Value("${app.ai.intent-router.shadow-export.include-raw-text:false}") boolean includeRawText
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.includeRawText = includeRawText;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean includeRawText() {
        return includeRawText;
    }

    public String hashUserText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public String toJsonl(List<IntentShadowEvalRecord> records) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (IntentShadowEvalRecord record : records) {
            if (record == null) {
                continue;
            }
            appendLine(sb, toJson(record));
        }
        return sb.toString();
    }

    public String toLegalAdviceLabelJsonl(List<LegalAdviceLabelRecord> records) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (LegalAdviceLabelRecord record : records) {
            if (record == null) {
                continue;
            }
            appendLine(sb, toJson(record));
        }
        return sb.toString();
    }

    public String toLegalAdviceLabelCsv(List<LegalAdviceLabelRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("consultation_id,message_id,expected_intent,actual_intent,legal_advice_risk,")
                .append("high_risk_leak,skip_false_positive,reviewer_role,review_comment");
        if (records == null || records.isEmpty()) {
            return sb.toString();
        }
        for (LegalAdviceLabelRecord record : records) {
            if (record == null) {
                continue;
            }
            sb.append('\n')
                    .append(record.consultationId()).append(',')
                    .append(record.messageId()).append(',')
                    .append(record.expectedIntent()).append(',')
                    .append(record.actualIntent()).append(',')
                    .append(record.legalAdviceRisk()).append(',')
                    .append(record.highRiskLeak()).append(',')
                    .append(record.skipFalsePositive()).append(',')
                    .append(csv(record.reviewerRole())).append(',')
                    .append(csv(record.reviewComment()));
        }
        return sb.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize shadow eval record", e);
        }
    }

    private void appendLine(StringBuilder sb, String line) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(line);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
