# API 명세: 대행사 알림센터 목록 조회 — type 필터 확장

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/notifications?type=AS_STATUS` |
| 역할 | AGENCY |
| 설명 | [get-notifications.md](./get-notifications.md)에서 구현한 알림센터 목록 조회 API에 `type` 쿼리 파라미터를 추가한다. URI·인증 방식·응답 구조·페이징 방식은 기존 API와 완전히 동일하며, `type`이 주어지면 `notifications.type`이 일치하는 알림만 `content`/페이징 결과에 포함시킨다. **별도의 신규 엔드포인트가 아니라 기존 API의 확장**이다. |

## 인증 / 권한

기존 API와 동일.

- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출
- 알림 수신 대상 범위 산정 로직(소속 기사 + 그 기사에게 A/S 받은 고객) 동일하게 적용

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 10 | 페이지 크기 |
| `type` | String | N | 없음(전체) | `AS_STATUS` / `CONSUMABLE` / `WARRANTY` / `LMS` 중 하나. 미입력 시 전체 타입 조회(기존 API와 동일 동작) |

## type 필터링 범위

- **`content`(목록), `totalElements`, `totalPages`, `currentPage`, `size`** → `type`이 주어지면 해당 타입으로 필터링된 결과
- **`stats`(totalCount/unreadCount/todayCount)** → `type` 필터와 무관하게 항상 대행사 전체 범위 기준 통계를 유지한다.
  - 이유: 프론트엔드 알림센터 화면에서 상단 통계 카드는 탭(타입) 전환과 무관하게 고정된 전체 현황을 보여주는 용도이고, 목록만 탭에 따라 필터링되는 UX 구조이기 때문 (기존 더미데이터 화면 구성 기준)

## 응답 DTO

`get-notifications.md`의 `AgencyNotificationResponse`와 동일한 구조를 그대로 사용한다. 추가 필드 없음.

## 비즈니스 로직 (변경/추가분만 기술)

1~4. `get-notifications.md`와 동일 (role 검증, 수신 대상 user_id 범위 산정, 빈 범위 단락 처리)
5. `type` 파라미터 검증: 값이 존재하면 `AS_STATUS/CONSUMABLE/WARRANTY/LMS` 중 하나인지 확인. 아니면 `IllegalArgumentException`(400)
6. `notificationRepository`에서 `user_id IN (...)` + `(type IS NULL OR type = :type)` 조건 + `Pageable`로 `Page<Notification>` 조회 → `content`/페이징 필드 매핑
7. `unreadCount`, `todayCount`는 type 필터를 적용하지 않고 기존과 동일하게 전체 범위로 집계
8. `totalCount` = type 필터링된 `page.getTotalElements()`가 아니라, **항상 전체 범위 totalCount**를 별도로 집계해서 사용 (목록의 totalElements와 stats.totalCount가 분리됨에 유의)

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY |
| `IllegalArgumentException` | 400 | `type` 값이 허용된 ENUM 4종에 해당하지 않음 |
| 정상(빈 결과) | 200 OK | 해당 type의 알림이 없는 경우에도 content 빈 배열로 200 반환 |

---

## 단위 테스트 명세 (JUnit 5)

> `AgencyNotificationServiceTest`에 케이스 추가 — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `type필터_적용시_해당타입만조회` | type="LMS" 전달 → repository에 type="LMS"로 전달되는지, content가 해당 타입만 포함하는지 검증 |
| `type미입력시_전체조회_기존과동일` | type=null 전달 → 기존 API와 동일하게 전체 조회되는지 검증 |
| `stats는_type필터와무관하게_전체범위유지` | type="LMS" 필터 적용해도 unreadCount/todayCount 계산에는 type 조건이 전달되지 않음을 검증 |
| `잘못된type값_IllegalArgumentException` | type="INVALID" 전달 → `IllegalArgumentException`, repository 호출 없음 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER + type 파라미터 포함 호출 → `IllegalAccessException` |

---

## 통합 테스트 명세 (H2 DB)

> `AgencyNotificationControllerIntegrationTest`에 `@Nested` 클래스 추가 — `@SpringBootTest` + `@AutoConfigureMockMvc` + H2

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `type_AS_STATUS_필터링_목록조회` | AS_STATUS 2건 + LMS 1건 저장 → `?type=AS_STATUS` 조회 시 content 2건, 모두 type=AS_STATUS |
| `type_미입력시_전체타입_반환` | 서로 다른 타입 3건 저장 → type 파라미터 없이 조회 시 3건 모두 반환 |
| `type_필터링되어도_stats_totalCount는_전체건수` | AS_STATUS 1건 + LMS 2건 저장 → `?type=AS_STATUS` 조회 시 `content.length()=1`이지만 `stats.totalCount=3` |
| `type_필터_+_페이징_동시적용` | AS_STATUS 15건 + LMS 5건 저장 → `?type=AS_STATUS&size=10` 조회 시 content 10건, totalElements=15(AS_STATUS 기준) |
| `잘못된type값_400` | `?type=INVALID_TYPE` 조회 → 400 Bad Request |
| `해당타입알림없음_200_빈배열` | 다른 타입만 존재 → `?type=WARRANTY` 조회 시 200 OK + content 빈 배열 |
