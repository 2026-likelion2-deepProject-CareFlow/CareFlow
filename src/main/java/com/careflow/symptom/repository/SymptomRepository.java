package com.careflow.symptom.repository;

import com.careflow.symptom.entity.Symptom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymptomRepository extends JpaRepository<Symptom, Long> {
    // 특정 카테고리의 활성 증상 목록 조회 (A/S 신청 화면 드롭다운용)
    List<Symptom> findByCategory_CategoryIdAndIsActiveTrue(Integer categoryId);
}
