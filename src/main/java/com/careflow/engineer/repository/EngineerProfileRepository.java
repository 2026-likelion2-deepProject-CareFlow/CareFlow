package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EngineerProfileRepository extends JpaRepository<EngineerProfile, Long> {
    boolean existsByUserId(Long userId);   // 중복 가입 확인
}
