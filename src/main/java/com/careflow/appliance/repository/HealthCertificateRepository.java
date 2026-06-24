package com.careflow.appliance.repository;
import com.careflow.appliance.entity.HealthCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HealthCertificateRepository extends JpaRepository<HealthCertificate, Long> {
    Optional<HealthCertificate> findByAppliance_Id(Long applianceId); // 가전 ID로 진단서 찾기!
}