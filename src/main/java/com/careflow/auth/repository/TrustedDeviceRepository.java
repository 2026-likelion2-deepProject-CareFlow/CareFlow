package com.careflow.auth.repository;

import com.careflow.auth.entity.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    List<TrustedDevice> findByUser_IdOrderByLastUsedAtDesc(Long userId);

    long countByUser_Id(Long userId);

    Optional<TrustedDevice> findByUser_IdAndDeviceToken(Long userId, String deviceToken);
}
