package com.careflow.as_status_log.dto;

import com.careflow.as_status_log.entity.AsStatusLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 소속 대행사 A/S 상태 변경 이력 목록 응답 DTO
 */
public record AsStatusLogListResponse(
        int totalCount,
        List<AsStatusLogItem> logs
) {
    public static AsStatusLogListResponse of(List<AsStatusLog> entities) {
        List<AsStatusLogItem> items = entities.stream()
                .map(AsStatusLogItem::from)
                .toList();
        return new AsStatusLogListResponse(items.size(), items);
    }

    public record AsStatusLogItem(
            Long logId,
            Long requestId,
            String fromStatus,
            String toStatus,
            String memo,
            String changedByName,
            LocalDateTime changedAt
    ) {
        public static AsStatusLogItem from(AsStatusLog log) {
            return new AsStatusLogItem(
                    log.getId(),
                    log.getAsRequest().getId(),
                    log.getFromStatus(),
                    log.getToStatus(),
                    log.getMemo(),
                    log.getChangedBy().getName(),
                    log.getCreatedAt()
            );
        }
    }
}
