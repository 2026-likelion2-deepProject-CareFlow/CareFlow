# API 명세: 대행사 로그인 보안 설정 (2단계 인증 / 로그인 알림)

## 개요

`Agencysettingpage.jsx` 우측 패널 "로그인 보안" 섹션(2단계 인증, 로그인 알림)에 대응하는 API. 두 항목 모두 로그인한 본인(대행사 관리자) 계정 기준으로 동작한다.

## 스키마 변경

`users` 테이블에 컬럼 2개 추가(모든 역할 공통 컬럼이며 기본값 false로 기존 계정에 영향 없음):

```sql
ALTER TABLE users
  ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '2단계 인증 사용 여부',
  ADD COLUMN login_alert_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '로그인 알림 사용 여부';
```

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
  "loginAlertEnabled": true
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

`login_alert_enabled = true`인 계정은, 로그인 성공 시(`AuthService.login()` 내부) 본인에게 "새 로그인 감지" 알림이 실제로 발송된다(`NotificationRepository.save(Notification.createAsStatusNotification(...))` 직접 저장 패턴 재사용).

---

## 테스트 명세

### 단위 테스트 (`AuthServiceTest`, `AgenciesServiceTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 보안 설정 조회 | twoFactorEnabled/loginAlertEnabled 정상 반환 |
| 2 | 2FA 토글 on→off | `User.two_factor_enabled` 갱신 확인(더티 체킹) |
| 3 | 로그인 알림 토글 | `User.login_alert_enabled` 갱신 확인 |
| 4 | 로그인 알림 활성화 상태 로그인 | 알림 저장 확인 |
| 5 | 로그인 알림 저장 중 예외 | 로그인 자체는 성공(토큰 반환) |

### 통합 테스트 (`AgencySecuritySettingsIntegrationTest` — H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 로그인 알림 on 상태에서 로그인 | 로그인 성공 후 `notifications` 테이블에 신규 행 생성 확인 |
| 2 | 로그인 알림 off 상태에서 로그인 | 로그인 성공하되 알림 미생성 확인 |
| 3 | GET 보안 설정 조회 | 상태값 정상 반환 확인 |

---

## 변경 이력

- 최초 구현 시 함께 도입되었던 "신뢰 기기" 기능(로그인 시 자동 기기 등록/목록 조회/삭제)은 애초 설계 범위에 없던 기능으로 판단되어 전체 제거되었다. 관련 테이블(`trusted_devices`), 엔드포인트(`GET/DELETE /api/agency/me/trusted-devices`), 프론트엔드 UI 및 `X-Device-Id` 헤더 전송 로직을 모두 삭제했다.
