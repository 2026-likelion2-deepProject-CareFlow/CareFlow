# API 명세: 진행 중 배정 목록 조회

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/as-assignments/in-progress` |
| 역할 | AGENCY |
| 설명 | 현재 로그인한 대행사 소속의 진행 중(ACCEPTED) 배정 목록을 필터·페이징하여 조회한다. stats 블록으로 전체 현황 요약을 함께 반환하여 프론트의 실시간 진행 현황 모니터링에 사용된다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출

## 요청 파라미터 (`@RequestParam`)

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 10 | 페이지 크기 |
| `date` | LocalDate (ISO) | N | - | 방문 예정일 필터 |
| `regionId` | Long | N | - | 지역 필터 |
| `latestLogStatus` | String | N | - | 최신 로그 상태 필터 (WAITING/ENGINEER_DEPARTED/ENGINEER_ARRIVED/IN_PROGRESS/COMPLETED) |
| `brand` | String | N | - | 브랜드 필터 |
| `engineerId` | Long | N | - | 기사 필터 |
| `keyword` | String | N | - | 접수번호·고객명·제품명 부분 일치 검색 |

> `date`, `regionId`, `brand`, `engineerId` 는 DB 쿼리 레벨에서 필터링되며,  
> `latestLogStatus`, `keyword` 는 로그 집계 후 서비스 레이어에서 인메모리 필터링됩니다.

## 응답 구조

```json
{
  "stats": {
    "totalCount": 25,
    "movingCount": 8,
    "inProgressCount": 14,
    "completedCount": 18
  },
  "content": [
    {
      "assignmentId": 1,
      "requestId": 1,
      "createdAt": "2024-06-18T00:00:00",
      "customerName": "김민지",
      "customerPhone": "010-1234-5678",
      "productName": "LG 디오스 냉장고",
      "modelNo": "M874GBB231",
      "engineerName": "김철수",
      "engineerPhone": "010-1111-2222",
      "engineerRating": 4.9,
      "assignMethod": "MANUAL",
      "assignmentStatus": "ACCEPTED",
      "latestLogStatus": "IN_PROGRESS",
      "visitDate": "2024-06-18",
      "visitTime": "10:00",
      "visitAddress": "서울특별시 강남구 역삼로 123-45",
      "updatedAt": "2024-06-18T10:20:00",
      "logs": [
        {
          "toStatus": "IN_PROGRESS",
          "memo": "작업을 시작했습니다.",
          "createdAt": "2024-06-18T10:00:00"
        }
      ],
      "stepTimes": {
        "ACCEPTED": "09:10",
        "WAITING": "09:10",
        "ENGINEER_DEPARTED": "09:35",
        "ENGINEER_ARRIVED": "09:50",
        "IN_PROGRESS": "10:00",
        "COMPLETED": null
      }
    }
  ],
  "totalElements": 24,
  "totalPages": 5,
  "currentPage": 0,
  "size": 10
}
```

## 응답 DTO

### `AssignmentInProgressPageResponse`
```java
record AssignmentInProgressPageResponse(
    AssignmentInProgressStats stats,
    List<AssignmentInProgressResponse> content,
    long totalElements,
    int totalPages,
    int currentPage,
    int size
)
```

### `AssignmentInProgressStats`
```java
record AssignmentInProgressStats(
    int totalCount,       // DB 필터 후 ACCEPTED 전체 건수 (latestLogStatus·keyword 필터 전)
    int movingCount,      // latestLogStatus IN (ENGINEER_DEPARTED, ENGINEER_ARRIVED)
    int inProgressCount,  // latestLogStatus = IN_PROGRESS
    int completedCount    // 대행사 소속 COMPLETED 배정 총 건수
)
```

### `AssignmentInProgressResponse`
```java
record AssignmentInProgressResponse(
    Long assignmentId,
    Long requestId,
    LocalDateTime createdAt,        // as_requests.created_at
    String assignMethod,            // AUTO / MANUAL

    Long customerId,
    String customerName,
    String customerPhone,

    String productName,             // brand + " " + modelName
    String modelNo,                 // appliances.serial_number

    Long engineerId,
    String engineerName,
    String engineerPhone,
    Double engineerRating,          // engineer_profiles.avg_rating
    String engineerImg,             // engineer_profiles.profile_image_url

    String assignmentStatus,        // as_assignments.status (ACCEPTED)
    String latestLogStatus,         // as_status_logs 최신 to_status (null 가능)
    LocalDateTime updatedAt,        // as_requests.updated_at

    String visitDate,               // as_requests.scheduled_date
    String visitTime,               // as_requests.scheduled_time
    String visitAddress,            // as_requests.visit_address_detail

    List<StatusLogEntry> logs,      // as_status_logs (최신순)
    Map<String, String> stepTimes   // 단계별 도달 시각 (HH:mm)
)

record StatusLogEntry(
    String toStatus,
    String memo,
    LocalDateTime createdAt
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. `findInProgressWithFilter` 로 ACCEPTED 배정 조회 (date·regionId·brand·engineerId DB 필터 적용)
3. 기사 프로필·as_status_logs 배치 조회 (N+1 방지)
4. 각 배정을 `AssignmentInProgressResponse`로 변환:
   - `latestLogStatus` = 가장 최근 로그의 `to_status` (없으면 null)
   - `stepTimes` = 각 단계의 최초 도달 시각(HH:mm). 도달 전 단계는 null
5. DB 필터 후 전체 리스트 기준으로 `stats` 계산
6. `latestLogStatus`, `keyword` 인메모리 필터 적용
7. 인메모리 페이지네이션 후 `AssignmentInProgressPageResponse` 반환

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY |

> 결과가 없어도 200 OK + empty content 반환 (stats 블록 항상 포함)

---

## 단위 테스트 명세 (JUnit 5)

> `AssignmentInProgressServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `진행중_배정없음_빈content_stats0반환` | ACCEPTED 배정 없음 → content 빈 배열, stats 모두 0 |
| `진행중_배정1건_필드매핑_정상` | ACCEPTED 1건 → 응답 필드 매핑 정확, createdAt 포함 |
| `engineer_프로필없음_rating_img_null` | 기사 프로필 없음 → engineerRating·engineerImg null |
| `engineer_프로필있음_rating_img_매핑` | 기사 프로필 있음 → engineerRating·engineerImg 매핑 |
| `statusLog_있음_latestLogStatus_로그목록_매핑` | 로그 존재 → latestLogStatus·logs·stepTimes 매핑 |
| `latestLogStatus_필터_인메모리_적용` | latestLogStatus 파라미터 전달 → 해당 상태만 content 반환 |
| `keyword_필터_고객명_부분일치` | keyword 파라미터 → 고객명 부분 일치 필터 적용 |
| `페이지네이션_2페이지_정상` | 총 3건, size=2, page=1 → content 1건 반환 |
| `stats_movingCount_inProgressCount_집계` | ENGINEER_DEPARTED 1건 + IN_PROGRESS 1건 → stats 값 정확 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER → IllegalAccessException |

---

## 통합 테스트 명세 (H2 DB)

> `AssignmentControllerIntegrationTest` — `@SpringBootTest` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `진행중_배정없음_200_빈content` | ACCEPTED 배정 없음 → 200 OK, content 빈 배열 |
| `진행중_배정1건_핵심필드_검증` | ACCEPTED 1건 → stats.totalCount=1, content[0] 필드 검증 |
| `다른대행사_배정_결과_미포함` | 다른 대행사 ACCEPTED 배정 → 응답 content에 포함되지 않음 |
| `statusLog_포함여부_확인` | AsStatusLog 1건 저장 → content[0].logs 배열에 포함 |
| `latestLogStatus_필터_적용` | ENGINEER_DEPARTED 로그 추가 후 latestLogStatus 파라미터 전달 → 해당 건만 반환 |
| `페이지네이션_page_size_파라미터_적용` | ACCEPTED 3건 저장, size=2, page=0 → content 2건·totalElements=3 |
| `CUSTOMER권한_401` | CUSTOMER 토큰 → 401 Unauthorized |
| `토큰없음_401` | 토큰 없음 → 401 Unauthorized |
