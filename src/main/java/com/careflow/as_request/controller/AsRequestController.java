package com.careflow.as_request.controller;

import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.service.AsRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/as-requests")
@RequiredArgsConstructor
public class AsRequestController {

    private final AsRequestService asRequestService;

    /**
     * 고객용: A/S 접수 신청 API
     */
    @PostMapping
    public ResponseEntity<String> createAsRequest(
            @RequestParam Long customerId, // 추후 @AuthenticationPrincipal 등으로 대체 예정
            @Valid @RequestBody AsRequestCreateDto dto) {

        Long requestId = asRequestService.createAsRequest(customerId, dto);
        return ResponseEntity.ok("A/S 접수가 성공적으로 완료되었습니다. (신청 ID: " + requestId + ")");
    }

    /**
     * 고객용: 본인의 A/S 목록 조회 API
     */
    @GetMapping("/me")
    public ResponseEntity<List<AsRequestResponseDto>> getMyAsRequests(@RequestParam Long customerId) {
        List<AsRequestResponseDto> myRequests = asRequestService.getMyAsRequests(customerId);
        return ResponseEntity.ok(myRequests);
    }

    /**
     * 고객용: A/S 취소 API
     */
    @PatchMapping("/{asRequestId}/cancel")
    public ResponseEntity<String> cancelAsRequest(
            @RequestParam Long customerId,
            @PathVariable Long asRequestId,
            @RequestParam(required = false) String cancelReason) {

        asRequestService.cancelAsRequest(customerId, asRequestId, cancelReason);
        return ResponseEntity.ok("성공적으로 A/S 요청이 취소되었습니다.");
    }
}