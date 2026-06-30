package com.careflow.user.repository;

import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // 대행사 소속 + 특정 role의 user_id 목록 조회 (알림 수신 대상 범위 산정용)
    @Query("SELECT u.id FROM User u WHERE u.agency.id = :agencyId AND u.role = :role")
    List<Long> findIdsByAgency_IdAndRole(@Param("agencyId") Long agencyId, @Param("role") Role role);
}