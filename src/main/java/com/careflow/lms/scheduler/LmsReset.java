package com.careflow.lms.scheduler;


import com.careflow.engineer.repository.EngineerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매년 1월 1일 00:00 실행
 * 전체 기사 engineer_profiles.is_lms_completed = 0 초기화
 * → 연간 LMS 이수 사이클 리셋
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LmsReset implements Job{

    private final EngineerProfileRepository engineerProfileRepository;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("[LMS 초기화] 연간 LMS 이수 상태 초기화 시작");

            int updatedCount = engineerProfileRepository.resetAllLmsCompleted();

            log.info("[LMS 초기화] 완료 — 초기화된 기사 수: {}명", updatedCount);

        } catch (Exception e) {
            log.error("[LMS 초기화] 실패: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
