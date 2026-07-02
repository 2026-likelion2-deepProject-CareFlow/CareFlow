// 파일 경로: src/main/java/com/careflow/settlement/service/EngineerSettlementService.java
package com.careflow.settlement.service;

import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineerSettlementService {

    private final SettlementRepository settlementRepository;

    // 기사용 정산 내역 페이징 조회
    public Page<Settlement> getSettlementsByEngineer(Long engineerId, Pageable pageable) {
        return settlementRepository.findPageByEngineerId(engineerId, pageable);
    }
}