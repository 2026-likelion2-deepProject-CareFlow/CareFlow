package com.careflow.quiz.scheduler;

import com.careflow.quiz.repository.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuizYearRolloverJob implements Job {

    private final QuizQuestionRepository quizQuestionRepository;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        int prevYear = LocalDate.now().getYear() - 1;
        int newYear  = LocalDate.now().getYear();

        // 1. 전년도 문항 아카이브
        int deactivated = quizQuestionRepository.deactivateByYear(prevYear);
        log.info("[QuizYearRolloverJob] {}년도 문항 아카이브 완료: {}건", prevYear, deactivated);

        // 2. 신년도 문항 활성화
        int activated = quizQuestionRepository.activateByYear(newYear);
        log.info("[QuizYearRolloverJob] {}년도 문항 활성화 완료: {}건", newYear, activated);

        // 3. 신년도 문항 미등록 계층 집계 (로그만 — 배너는 프론트 API 실시간 조회)
        List<Object[]> underRegistered = quizQuestionRepository.findUnderRegisteredTiers(newYear);
        if (!underRegistered.isEmpty()) {
            log.warn("[QuizYearRolloverJob] {}년도 문항 미등록/미완성 계층 {}개 존재. " +
                    "관리자 대시보드 확인 필요.", newYear, underRegistered.size());
        } else {
            log.info("[QuizYearRolloverJob] {}년도 전체 계층 문항 등록 완료 확인.", newYear);
        }
    }
}
