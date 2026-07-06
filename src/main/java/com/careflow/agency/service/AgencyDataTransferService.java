package com.careflow.agency.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.response.AgencyDataImportResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 대행사 설정 화면 "데이터 관리"(내보내기/가져오기) 서비스
 * - 내보내기: 소속 기사 로스터를 CSV로 백업
 * - 가져오기: CSV로 기사 가입 신청(account_requests, PENDING)을 일괄 등록 — 바로 계정을 만들지 않고
 *   기존 승인/반려 워크플로우에 편입시켜 대행사 관리자가 건별로 검토하도록 한다
 */
@Service
@RequiredArgsConstructor
public class AgencyDataTransferService {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final EngineerProfileRepository engineerProfileRepository;
    private final AccountRequestsRepository accountRequestsRepository;
    private final AgenciesRepository agenciesRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public byte[] exportEngineerRoster(Long agencyId) {
        List<EngineerProfile> profiles = engineerProfileRepository.findByAgencyId(agencyId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.writeBytes(BOM);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.println("engineerUserId,name,email,phone,categoryName,skillLevel,status,avgRating");
            for (EngineerProfile profile : profiles) {
                User user = profile.getUser();
                writer.println(String.join(",",
                        String.valueOf(user.getId()),
                        escapeCsvField(user.getName()),
                        escapeCsvField(user.getEmail()),
                        escapeCsvField(user.getPhone()),
                        escapeCsvField(profile.getCategory() != null ? profile.getCategory().getName() : ""),
                        profile.getSkillLevel() != null ? profile.getSkillLevel().name() : "",
                        user.getStatus(),
                        profile.getAvgRating() != null ? profile.getAvgRating().toPlainString() : ""
                ));
            }
            writer.flush();
        }
        return baos.toByteArray();
    }

    /**
     * CSV(name,email,phone) 한 줄씩 파싱하여 기사 가입 신청(PENDING)을 일괄 생성.
     * 대표 담당자만 호출 가능 — 일괄 계정 생성을 시작하는 민감한 동작이므로 일반 관리자는 제외.
     * 행 단위 부분 성공 허용(전체 롤백 아님) — 실패한 행은 건너뛰고 사유를 errors에 담아 반환.
     */
    @Transactional
    public AgencyDataImportResponse importEngineerRoster(CustomUserDetails userDetails, MultipartFile file)
            throws IllegalAccessException {

        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }
        boolean isRepresentative = agenciesRepository.findByRepresentativeById(userDetails.getUserId()).isPresent();
        if (!isRepresentative) {
            throw new IllegalAccessException("대표 담당자만 기사 로스터를 일괄 등록할 수 있습니다.");
        }

        Agencies agency = agenciesRepository.findById(userDetails.getAgencyId())
                .orElseThrow(() -> new NoSuchElementException("대행사 정보를 찾을 수 없습니다."));

        int successCount = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // 헤더 행 스킵
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 2) {
                    errors.add(rowNum + "행: 형식이 올바르지 않습니다. (name,email,phone 필요)");
                    continue;
                }

                String name = cols[0].trim();
                String email = cols[1].trim();
                String phone = cols.length > 2 ? cols[2].trim() : null;

                if (name.isBlank() || email.isBlank()) {
                    errors.add(rowNum + "행: 이름과 이메일은 필수입니다.");
                    continue;
                }
                if (userRepository.existsByEmail(email) || accountRequestsRepository.existsByEmail(email)) {
                    errors.add(rowNum + "행: 이미 가입된 이메일입니다.");
                    continue;
                }

                String tempPassword = generateTempPassword();
                AccountRequests request = AccountRequests.create(
                        agency, email, passwordEncoder.encode(tempPassword), name,
                        phone != null && !phone.isBlank() ? phone : null,
                        AccountRequestsRole.ENGINEER, null, null);
                accountRequestsRepository.save(request);
                successCount++;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("CSV 파일을 읽을 수 없습니다.");
        }

        return new AgencyDataImportResponse(successCount, errors.size(), errors);
    }

    private String generateTempPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private String escapeCsvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
