package org.example.shield.brief.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.shield.brief.application.BriefService;
import org.example.shield.brief.controller.dto.BriefResponse;
import org.example.shield.brief.controller.dto.BriefSummaryResponse;
import org.example.shield.brief.controller.dto.BriefUpdateRequest;
import org.example.shield.brief.controller.dto.BriefUpdateResponse;
import org.example.shield.common.response.ApiResponse;
import org.example.shield.common.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Brief", description = "의뢰서 API")
@RestController
@RequestMapping("/api/briefs")
@RequiredArgsConstructor
public class BriefController {

    private final BriefService briefService;

    @Operation(summary = "내 의뢰서 목록", description = "로그인한 사용자의 의뢰서 목록을 조회합니다")
    @GetMapping
    public ApiResponse<PageResponse<BriefSummaryResponse>> getMyBriefs(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<BriefSummaryResponse> result = briefService.getMyBriefs(userId, pageable);
        return ApiResponse.success("조회 성공", result);
    }

    @Operation(summary = "의뢰서 상세 조회", description = "의뢰서의 상세 내용을 조회합니다")
    @GetMapping("/{briefId}")
    public ApiResponse<BriefResponse> getBrief(
            @PathVariable UUID briefId,
            @AuthenticationPrincipal UUID userId) {
        BriefResponse result = briefService.getBrief(briefId, userId);
        return ApiResponse.success("조회 성공", result);
    }

    @Operation(summary = "의뢰서 수정", description = "의뢰서를 수정합니다 (내용, 상태, 개인정보 설정)")
    @PatchMapping("/{briefId}")
    public ApiResponse<BriefUpdateResponse> updateBrief(
            @PathVariable UUID briefId,
            @AuthenticationPrincipal UUID userId,
            @RequestBody BriefUpdateRequest request) {
        BriefUpdateResponse result = briefService.updateBrief(briefId, userId, request);
        return ApiResponse.success("의뢰서가 수정되었습니다", result);
    }
}
