# 고객 리뷰 작성 API 설계

## 개요

결제 완료(`status = PAID`) 후 고객이 담당 수리 기사에 대한 평점과 리뷰를 작성하는 API.
리뷰 저장 즉시 `engineer_profiles.avg_rating` 및 `total_reviews`를 재계산하여 갱신한다.
기사는 `work_reports.engineer_id`를 출처로 사용한다 (배정 기사가 아닌 실제 보고서 제출 기사).

명세: **C-23**

---

## API — 기사 평점/리뷰 작성

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `POST` |
| Path | `/api/customer/as-requests/{requestId}/reviews` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `review` |

### Request Body

```json
{
  "rating": 5,
  "content": "친절하고 빠르게 수리해 주셨습니다."
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `rating` | Integer | ✅ | 1 이상 5 이하 |
| `content` | String | ❌ | 자유 텍스트 |

### 처리 흐름

1. JWT에서 `customerId` 추출
2. `requestId`로 `as_requests` 단건 조회 — 없으면 404
3. `asRequest.customer_id == customerId` 검증 — 불일치 시 401
4. `as_requests.status == PAID` 확인 — 아니면 403
5. `reviews`에 동일 `request_id`로 이미 작성된 리뷰가 있는지 확인 — 있으면 403
6. `work_reports`에서 `request_id`로 보고서 조회 → `engineer` 추출 (실제 작업 기사)
7. `Review.create()` 정적 팩토리로 저장
8. `engineer_profiles.avg_rating`, `total_reviews` 재계산 후 갱신
   - `newAvg = (기존 평균 × 기존 건수 + 새 평점) / (기존 건수 + 1)`
9. 201 + `ReviewResponse` 반환

### 응답 예시 (201 Created)

```json
{
  "reviewId": 7,
  "requestId": 12,
  "engineerId": 5,
  "rating": 5,
  "content": "친절하고 빠르게 수리해 주셨습니다.",
  "createdAt": "2026-06-29T15:00:00"
}
```

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않는 requestId | 404 | `NoSuchElementException` |
| 본인 요청이 아님 | 401 | `IllegalAccessException` |
| status != PAID | 403 | `IllegalStateException` |
| 이미 리뷰 작성 완료 | 403 | `IllegalStateException` |
| 작업 완료 보고서 없음 | 404 | `NoSuchElementException` |
| 기사 프로필 없음 | 404 | `NoSuchElementException` |
| rating 범위 위반 (1 미만 / 5 초과) | 400 | `MethodArgumentNotValidException` |

---

## DB 설계 참조

```sql
-- reviews (핵심 컬럼만)
review_id   BIGINT PK
request_id  BIGINT FK → as_requests.request_id  UNIQUE
customer_id BIGINT FK → users.user_id
engineer_id BIGINT FK → users.user_id
rating      INT NOT NULL              -- 1~5
content     TEXT NULL
is_visible  TINYINT(1) DEFAULT 1
created_at  DATETIME DEFAULT CURRENT_TIMESTAMP

INDEX idx_review_engineer (engineer_id, created_at)
INDEX idx_review_customer (customer_id)

-- engineer_profiles (역정규화 필드)
avg_rating    DECIMAL(3,2) DEFAULT 0.00   -- 리뷰 저장 후 즉시 갱신
total_reviews INT DEFAULT 0              -- 리뷰 저장 후 즉시 갱신
```

---

## 구현 위치

```
review/
├── controller/ReviewController.java
├── service/ReviewService.java
├── entity/Review.java
├── repository/ReviewRepository.java
└── dto/
    ├── ReviewCreateRequest.java
    └── ReviewResponse.java

engineer/
└── domain/entity/EngineerProfile.java   (updateRating() 도메인 메서드 추가)
```

---

## 테스트 전략

### 단위 테스트 (`ReviewServiceTest`)

- `@ExtendWith(MockitoExtension.class)`
- 검증 항목:
  - 정상 리뷰 작성 흐름 및 avgRating 재계산 정확성
  - status != PAID → 403
  - 중복 리뷰 시도 → 403
  - 타인 요청에 리뷰 시도 → 401
  - rating 범위 위반 → 400

### 통합 테스트

- `@SpringBootTest` + H2 인메모리
- 정상 흐름: 상태를 PAID까지 올린 뒤 POST 리뷰 → `reviews` row 확인, `engineer_profiles.avg_rating` 갱신 확인
- 동일 건 재작성 시도 → 403 확인
