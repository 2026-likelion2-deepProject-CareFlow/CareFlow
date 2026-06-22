package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EngineerProfileRepository extends JpaRepository<EngineerProfile, Long> {
    boolean existsByUser_UserId(Long userId);   // 중복 가입 로직
    Optional<EngineerProfile> findByUser_UserId(Long userId);
}
