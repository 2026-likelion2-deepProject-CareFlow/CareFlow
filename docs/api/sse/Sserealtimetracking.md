# 🚀 API 생성 및 개발 요구사항 정의서 — SSE 기반 실시간 A/S 상태 트래킹 (Realtime Tracking)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `notifications`(알림 발송 로그), `as_status_logs`(현장 상태 기록 = SSE 원천), `as_requests`, `users`, `agencies` 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `Notification`(notifications) 직접 구현. `AsStatusLog`(as_status_logs) 는 A/S 도메인과 공유(상태 기록은 담당자 C 책임). `User`·`AsRequest`·`Agencies` 는 재사용.
- 주요 컬럼 (`notifications`) : `type`(VARCHAR, 예: `AS_STATUS`), `title`(VARCHAR), `body`(TEXT), `channel`(VARCHAR — Phase 1 은 `SSE` 고정), `created_at`
- 핵심 제약 / 설계 원칙
    - `notifications` 에는 **읽음/안읽음 컬럼이 없다** → **발송 기록(로그)** 용도. "안 읽은 알림 뱃지"는 현 구조로 미지원.
    - **SSE 연결(Emitter) 은 DB가 아닌 인메모리(`ConcurrentHashMap`)** 로 관리한다. 영속 대상은 알림 로그(`notifications`)와 상태 로그(`as_status_logs`) 뿐이다.
    - Emitter 식별자(`emitterId`)는 `{userId}_{발급시각ms}` 형식 → **한 사용자(멀티탭/멀티기기)당 여러 Emitter** 허용.
    - SSE의 이벤트 원천(source of truth)은 `as_status_logs` 이다. 상태 변경 시 **상태 로그를 먼저 기록한 뒤** 알림을 발송한다.

## 2. API 엔드포인트 명세
본 도메인은 ① 클라이언트의 **구독(SSE)** 과 ② 비즈니스 이벤트에 의한 **푸시(내부 트리거)** 두 흐름으로 구성된다.
- 공통 : 구독은 JWT 인증 필요, `@AuthenticationPrincipal CustomUserDetails` 에서 `userId` 추출. SSE 구독 경로(`/api/sse/subscribe`)는 RESTful 공용 경로로 유지(변경 없음).

### (A) [GET] /api/sse/subscribe - SseController.subscribe
- **설명**: 로그인한 사용자(고객·대행사 대표 등)가 실시간 알림 채널을 구독한다. 연결은 `text/event-stream` 으로 유지된다.
- **Produces**: `text/event-stream` (`MediaType.TEXT_EVENT_STREAM_VALUE`)
- **Request Header**: `Last-Event-ID`(String, 선택) — 재연결 시 마지막 수신 이벤트 ID (현재 캐시 재전송은 미구현, 3장 참조)
- **Response (200 OK)**: `SseEmitter` (기본 타임아웃 1시간). 구독 직후 503 방지용 **더미 이벤트 1건**을 즉시 발송한다.
- **이벤트 포맷**: `id = {userId}_{ms}`, `name = "sse"`(프론트가 수신할 이벤트 이름), `data = Notification 객체(JSON)`

### (B) [내부 트리거] 현장 상태 변경 — PATCH /api/engineer/tasks/{requestId}/status
- **설명**: 기사가 작업 단계(출발/도착/작업시작)를 변경하면, **동일 트랜잭션 내**에서 `as_status_logs` 를 기록하고 고객·대행사 대표에게 알림을 발송한다. (상태 변경 API 자체는 `EngineerTaskController` 소관)
- **Path/Query**: `requestId`(Long), `@RequestParam AsStatus newStatus` (`ENGINEER_DEPARTED` / `ENGINEER_ARRIVED` / `IN_PROGRESS`)
- **권한**: `role = ENGINEER` 만 허용(그 외 거부)
- **트리거 흐름**: `AsRequestService.updateEngineerTaskStatus()` → 상태 가드 검증 → `AsStatusLog` 저장 → `notificationService.send(고객, ...)` + `send(대행사 대표, ...)`
- **Response (200 OK)**: 상태 변경 결과 안내 문자열. 알림 발송은 부수 효과로 수행된다.

### (C) [내부 트리거] 보고서 제출/승인 — WorkReportService
- **설명**: 작업 완료 보고서 제출(`POST /api/engineer/work-reports`) 및 고객 승인(`PATCH /api/engineer/work-reports/{reportId}/approve`) 시점에도 동일하게 `notificationService.send()` 로 알림을 발송한다. (보고서 흐름은 `workCompletionReport.md` 참조)

### (D) [GET] /api/engineer/notifications - (권장, 현재 미구현)
- **설명**: 사용자의 알림 발송 로그 목록을 최신순 조회한다. 가이드 STEP 7 알림 목록 화면 대응. **현재 소스에 엔드포인트 없음 — 추가 권장.**
- **Response (200 OK)**: `List<NotificationResponse>`(type, title, body, channel, createdAt) — 정적 팩토리 `from()` 권장

## 3. 상세 처리 로직 (Pipeline)

### (A) 구독 — subscribe(userId, lastEventId)
1. `emitterId = userId + "_" + System.currentTimeMillis()` 생성 후 `new SseEmitter(1시간)` 저장(`EmitterRepository.save`)
2. **연결 종료 정리 콜백 3종** 등록 : `onCompletion` · `onTimeout` · `onError` → 각각 `deleteById(emitterId)` (dead emitter 누수 방지)
3. **503 방지 더미 이벤트** : 구독 직후 `sendToClient(...)` 로 "EventStream Created" 1건 발송 (최초 연결 직후 데이터 미수신 시 브라우저가 503으로 처리하는 문제 방지)
4. (개선 권장) `lastEventId` 가 존재하면 캐시에서 유실 이벤트 재전송 — **현재 TODO(미구현)**

### (B) 발송 — send(receiver, type, title, body)
1. **알림 로그 적재** : `Notification`(channel=`SSE`) 빌드 후 `notificationRepository.save()` (Phase 1 로그)
2. **수신자 Emitter 조회** : `findAllEmitterStartWithByUserId(receiverId)` — 해당 사용자의 모든 연결(멀티탭) 조회
3. **푸시** : 연결된 각 Emitter 에 `sendToClient(emitter, key, eventId, notification)` 로 이벤트 발송 (Phase 2 실시간)

### (C) 전송 — sendToClient(emitter, emitterId, eventId, data)
- `emitter.send(event().id(eventId).name("sse").data(data))`
- `IOException`(끊긴 연결) 발생 시 → `deleteById(emitterId)` 로 dead emitter 제거 + 에러 로그

### ⚠ 개선 권장 사항 (정합성·안정성)
1. **[High] 커밋 이후 발송으로 분리** : 현재 알림 발송이 상태 변경 `@Transactional` **내부(커밋 전)** 에서 일어난다. 롤백 시 "고객은 푸시를 이미 받았는데 DB 는 변경되지 않은" 불일치가 생긴다. `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` 로 **커밋된 변경에 대해서만 발송**하도록 분리할 것 (알림 저장 실패가 본 작업을 롤백시키는 책임 분리 문제도 함께 해결).
2. **[Med] eventCache 정리** : 캐시에 쌓되 읽지도 비우지도 않아(재전송 미구현) **무한 증가(메모리 누수)** 한다. Phase 1 에서는 캐시를 제거하거나, `Last-Event-ID` 재전송과 eviction 을 함께 구현할 것.
3. **[Nit] 예외 범위** : `sendToClient` 가 `IOException` 만 처리한다. emitter 가 동시에 완료/타임아웃된 경우 `IllegalStateException` 도 발생할 수 있어 처리 범위를 넓히면 견고하다.

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것. (프로젝트 공통 `ErrorResponse` + `GlobalExceptionHandler` 사용)
- 상태 변경 API 는 `role = ENGINEER` 만 허용. 상태 전이는 직전 상태 가드로 검증(출발→도착→작업시작 순서 강제).
- 구독은 인증 사용자만 가능(`@AuthenticationPrincipal`). 미인증 시 `401`.
- SSE 전송 실패(끊긴 연결)는 **사용자 요청 실패로 전파하지 않고** 해당 Emitter 만 정리한다(알림은 best-effort).
- (개선 적용 시) 알림 발송은 본 비즈니스 트랜잭션의 성패에 영향을 주지 않는다(AFTER_COMMIT).

## 6. 개발 및 출력 요구사항
- 컨트롤러(`SseController`) · 서비스(`NotificationService`) · 리포지토리(`EmitterRepository`, `NotificationRepository`) · 엔티티(`Notification`) 레이어를 분리하여 구현할 것 (package-by-feature)
- Emitter 저장소는 **`ConcurrentHashMap`(thread-safe)** 으로 구현하고, **사용자당 다중 Emitter(멀티탭/기기)** 를 지원할 것
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@Setter`/`@Data` 금지
- **프론트 연동 계약(중요)**
    - 구독 : `const es = new EventSource("/api/sse/subscribe")` — 단, EventSource 는 헤더를 못 실으므로 **JWT 전달 방식**(쿼리 토큰 또는 쿠키 인증)을 팀과 합의
    - 수신 : `es.addEventListener("sse", (e) => { const n = JSON.parse(e.data); ... })` — 이벤트 이름 **`"sse"`** 고정
    - 재연결 : EventSource 는 타임아웃 시 자동 재연결하며, 갭 구간 이벤트 유실 방지는 `Last-Event-ID` 재전송(개선 권장) 구현 이후 보장됨
- 단위 테스트(JUnit5 + Mockito)와 통합 테스트(`@SpringBootTest` + H2)를 함께 작성할 것
    - 구독 시 Emitter 등록·더미 이벤트 발송 검증, 발송 시 `notifications` 적재 + 연결된 Emitter 푸시 검증, IOException 시 Emitter 제거 검증, 멀티 Emitter(같은 userId 2개) 동시 수신 검증