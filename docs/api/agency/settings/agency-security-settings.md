# API 명세: 대행사 로그인 보안 설정 (2단계 인증 / 로그인 알림 / 신뢰 기기)

## 개요

`Agencysettingpage.jsx` 우측 패널 "로그인 보안" 섹션(2단계 인증, 로그인 알림, 신뢰 기기 관리)에 대응하는 API. 세 항목 모두 로그인한 본인(대행사 관리자) 계정 기준으로 동작한다.

## 스키마 변경

`users` 테이블에 컬럼 2개 추가(모든 역할 공통 컬럼이며 기본값 false로 기존 계정에 영향 없음):

```sql
ALTER TABLE users
  ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '2단계 인증 사용 여부',
  ADD COLUMN login_alert_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '로그인 알림 사용 여부';
```

신규 테이블 `trusted_devices` 추가(신뢰 기기 목록):

```sql
CREATE TABLE `trusted_devices` (
    `device_id`    BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '기기 고유 ID',
    `user_id`      BIGINT UNSIGNED NOT NULL                            COMMENT '소유 사용자 ID',
    `device_name`  VARCHAR(255)    NOT NULL                            COMMENT 'User-Agent 기반 표시용 기기 이름(최신값으로 갱신됨)',
    `device_token` VARCHAR(64)     NOT NULL DEFAULT ''                 COMMENT '클라이언트 발급 기기 식별 토큰(UUID). 미전달 시 User-Agent 기반 대체값 사용',
    `last_used_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최근 사용 일시',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '최초 등록일',

    CONSTRAINT FK_users_TO_trusted_devices
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),

    UNIQUE uk_trusted_device_user_token (user_id, device_token),
    INDEX  idx_trusted_devices_user (user_id)
);
```

- **기기 식별 방식(2차 개선)**: 최초 구현은 `User-Agent` 문자열만으로 기기를 구분했으나, 브라우저가 업데이트되면 버전 숫자가 바뀌어 같은 기기가 새 기기로 오인식되는 문제가 있었다. 이를 개선해 프론트가 `localStorage`에 발급/보관하는 UUID(`deviceId.js`의 `getDeviceId()`)를 `X-Device-Id` 요청 헤더로 함께 보내고, 서버는 이 값을 `device_token`으로 저장해 **실제 매칭 키**로 사용한다. `device_name`은 표시용 라벨로만 쓰이며, 같은 `device_token`으로 재로그인하면 최신 User-Agent로 갱신된다.
- **하위 호환**: `X-Device-Id` 헤더가 없는 요청(구버전 클라이언트 등)은 `"ua:" + User-Agent.hashCode()`를 대체 토큰으로 사용해 기존과 동일하게 동작한다.
- 로그인마다 동일 `device_token`이면 `last_used_at`(+`device_name`)만 갱신, 신규 토큰이면 새 행 삽입 — 로그인 시점에 **자동 등록**되므로 별도의 "등록" API는 없음.

---

## 1. 로그인 보안 설정 조회

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `GET` |
| URL | `/api/agency/me/security` |
| 인증 | JWT 필수 (`AGENCY`) |

### 응답 `200 OK`

```json
{
  "twoFactorEnabled": true,
  "loginAlertEnabled": true,
  "trustedDeviceCount": 3
}
```

---

## 2. 2단계 인증 토글

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `PATCH` |
| URL | `/api/agency/me/security/two-factor` |
| 인증 | JWT 필수 (`AGENCY`) |

### Request Body

```json
{ "enabled": false }
```

### 응답

`204 No Content`

> MVP 범위: 실제 OTP 발급/검증 인프라(SMS·앱 인증) 없이 온/오프 상태값만 저장한다. 이 상태를 실제 로그인 2차 인증 절차에 연동하는 것은 별도 과제로, 본 API는 "설정 화면에서 상태를 토글하고 그 상태가 영구 저장되는" 부분까지만 구현한다.

---

## 3. 로그인 알림 토글

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `PATCH` |
| URL | `/api/agency/me/security/login-alert` |
| 인증 | JWT 필수 (`AGENCY`) |

### Request Body

```json
{ "enabled": true }
```

### 응답

`204 No Content`

### 실동작 연동

`login_alert_enabled = true`인 계정은, 로그인 성공 시(`AuthService.issueTokenResponse()` 내부) 본인에게 "새 로그인 감지" 알림이 실제로 발송된다(`NotificationRepository.save(Notification.createAsStatusNotification(...))` 직접 저장 패턴 재사용, type은 기존 enum 값 중 의미상 가장 가까운 `AS_STATUS`를 그대로 사용 — 별도 알림 타입 추가는 DDL 변경 범위를 최소화하기 위해 보류).

---

## 4. 신뢰 기기 목록 조회

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `GET` |
| URL | `/api/agency/me/trusted-devices` |
| 인증 | JWT 필수 (`AGENCY`) |

### 응답 `200 OK`

```json
[
  { "deviceId": 1, "deviceName": "Mozilla/5.0 ... Chrome/126", "lastUsedAt": "2026-07-06T10:00:00", "createdAt": "2026-06-01T09:00:00" }
]
```

---

## 5. 신뢰 기기 삭제(신뢰 해제)

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `DELETE` |
| URL | `/api/agency/me/trusted-devices/{deviceId}` |
| 인증 | JWT 필수 (`AGENCY`) |

### 응답

`204 No Content`

### 비즈니스 로직

- `{deviceId}`가 존재하지 않으면 `NoSuchElementException`(404).
- `{deviceId}`가 본인 소유가 아니면 `IllegalAccessException`(401) — 타인 기기 삭제 방지.
- 삭제해도 해당 기기에서의 기존 로그인 세션(JWT)은 무효화되지 않는다(Stateless 구조의 한계, 로그아웃/토큰 만료로만 종료). 다음 로그인 시 다시 신규 기기로 등록됨.

---

## 테스트 명세

### 단위 테스트 (`AgencySecuritySettingsServiceTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 보안 설정 조회 | twoFactorEnabled/loginAlertEnabled/trustedDeviceCount 정상 반환 |
| 2 | 2FA 토글 on→off | `User.two_factor_enabled` 갱신 확인(더티 체킹) |
| 3 | 로그인 알림 토글 | `User.login_alert_enabled` 갱신 확인 |
| 4 | 신뢰 기기 목록 조회 | 본인 소유 기기만 반환 |
| 5 | 신뢰 기기 삭제 성공 | repository.delete 호출 검증 |
| 6 | 타인 기기 삭제 시도 | `IllegalAccessException` |
| 7 | 존재하지 않는 기기 삭제 시도 | `NoSuchElementException` |

### 통합 테스트 (`AgencySecuritySettingsControllerIntegrationTest` — H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 2FA 토글 → 재조회 | `204` 후 `GET .../security`에서 반영 확인 |
| 2 | 로그인 알림 on 상태에서 로그인 | 로그인 성공 후 `notifications` 테이블에 신규 행 생성 확인 |
| 3 | 로그인 알림 off 상태에서 로그인 | 로그인 성공하되 알림 미생성 확인 |
| 4 | 로그인 시 신뢰 기기 자동 등록 | 첫 로그인 후 `trusted_devices`에 1건 생성, 동일 User-Agent로 재로그인 시 `last_used_at`만 갱신(행 개수 불변) 확인 |
| 5 | 신뢰 기기 삭제 | `204` + DB에서 행 삭제 확인 |
