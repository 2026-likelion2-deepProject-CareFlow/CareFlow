# API 명세: 월별 정산 리포트 다운로드

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP Method | GET |
| URL | `/api/settlements/monthly-report/download` |
| 인증 | 필수 (JWT) |
| 허용 역할 | `AGENCY` |
| 도메인 패키지 | `com.careflow.settlement` |

---

## 요청

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | O | 조회 연도 (예: 2026) |
| `month` | int | O | 조회 월 (1~12) |

### 요청 예시

```
GET /api/settlements/monthly-report/download?year=2026&month=6
Authorization: Bearer {accessToken}
```

---

## 응답

### 성공 (200 OK)

```
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="settlement_report_2026_06.csv"
```

**CSV 파일 구조** (섹션 1: 기사별 실적, 섹션 2: 합산 내역)

```
[기사별 실적]
기사ID,기사명,완료건수,평균평점,실수령액(원)
10,홍길동,12,4.75,960000
11,이순신,8,4.25,640000

[월별 합산]
총건수,총매출(원),CareFlow수수료(원),대행사수수료(원),기사지급액합계(원)
20,4000000,400000,360000,3240000
```

> - 파일명 형식: `settlement_report_{year}_{month:2자리}.csv`
> - 인코딩: UTF-8 with BOM (Excel에서 한글 깨짐 방지)
> - 데이터가 없는 월도 헤더와 빈 데이터 행 포함하여 파일을 반환한다 (204 No Content 아님).

### 실패 응답

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| JWT 없거나 만료 | 401 | - |
| AGENCY 역할 아님 | 403 | - |
| year/month 누락 또는 범위 초과 | 400 | `"월은 1~12 사이여야 합니다."` |

---

## 비즈니스 로직

1. JWT에서 `agency_id`를 추출한다 (`CustomUserDetails` → `users.agency_id`).
2. `engineer-performance` API의 기사별 실적 집계 로직을 그대로 재사용하여 기사별 데이터를 가져온다.
3. `monthly-summary` API의 합산 집계 로직을 그대로 재사용하여 월별 합산 데이터를 가져온다.
4. 두 데이터를 합쳐 CSV 바이트 배열로 변환한다.
5. `HttpServletResponse`에 스트리밍하거나 `byte[]`를 `ResponseEntity<byte[]>`로 반환한다.
6. UTF-8 BOM(`﻿`)을 파일 앞에 삽입하여 Excel 한글 깨짐을 방지한다.

---

## 참조 테이블

```
settlements
  - engineer_id, agency_id
  - gross_amount, platform_fee, agency_fee, engineer_net_amount
  - status, paid_at

reviews
  - engineer_id, rating, created_at

users
  - user_id, name
```

---

## 구현 파일 목록

| 파일 | 경로 |
|---|---|
| Controller | `settlement/controller/SettlementController.java` |
| Service | `settlement/service/SettlementService.java` |
| CSV 생성 유틸 | `settlement/service/SettlementCsvGenerator.java` |
| Repository | `settlement/repository/SettlementRepository.java` |
| Entity | `settlement/entity/Settlements.java` |

> `SettlementController`, `SettlementService`, `SettlementRepository`는 앞선 두 API와 동일 클래스를 공유한다.
> CSV 생성 로직은 `SettlementCsvGenerator`로 분리하여 단독 단위 테스트가 가능하게 한다.

---

## 테스트 요구사항

> **이 API를 구현할 때 아래 두 종류의 테스트를 반드시 작성해야 한다.**
> 테스트 없이 구현 완료로 간주하지 않는다.

### 1. 단위 테스트 (JUnit 5 + Mockito)

#### 1-1. SettlementService 단위 테스트

**테스트 클래스**: `src/test/java/com/careflow/settlement/service/SettlementServiceTest.java`

> `engineer-performance`, `monthly-summary` API와 동일 테스트 클래스에 메서드를 추가한다.

**필수 테스트 케이스**:

| 케이스 | 설명 |
|---|---|
| 정상 CSV 데이터 반환 | 기사별 실적과 합산 집계가 올바르게 결합된 데이터를 반환하는지 검증 |
| 데이터 없는 월 | 빈 기사 목록 + 0 합산값으로 빈 CSV 데이터를 반환하는지 검증 |

#### 1-2. SettlementCsvGenerator 단위 테스트

**테스트 클래스**: `src/test/java/com/careflow/settlement/service/SettlementCsvGeneratorTest.java`

**작성 규칙**:
- 외부 의존성 없이 순수 Java 단위 테스트로 작성
- `@ExtendWith(MockitoExtension.class)` 불필요 (순수 단위 테스트)

**필수 테스트 케이스**:

| 케이스 | 설명 |
|---|---|
| UTF-8 BOM 삽입 확인 | 반환된 `byte[]`의 첫 3바이트가 BOM(`0xEF, 0xBB, 0xBF`)인지 검증 |
| 헤더 행 포함 확인 | CSV 첫 번째 섹션에 기사별 헤더 행이 포함되는지 검증 |
| 데이터 행 값 검증 | 특정 기사의 completedCount, avgRating, totalEarning이 CSV에 올바르게 직렬화되는지 검증 |
| 빈 데이터 | 기사 목록이 비어있어도 헤더와 합산 섹션은 포함되는지 검증 |
| 특수문자 이름 | 기사 이름에 쉼표(`,`)나 큰따옴표(`"`)가 포함될 경우 CSV 이스케이프가 올바르게 처리되는지 검증 |

---

### 2. 통합 테스트 (H2 인메모리 DB)

**대상**: `SettlementController` — 실제 HTTP 요청 → 파일 다운로드 응답 전체 흐름

**테스트 클래스**: `src/test/java/com/careflow/settlement/controller/SettlementControllerTest.java`

> `engineer-performance`, `monthly-summary` API와 동일 테스트 클래스에 메서드를 추가한다.

**작성 규칙**:
- `@WebMvcTest(SettlementController.class)` + `@Import(SecurityConfig.class)` 사용
- `@MockitoBean`으로 서비스 레이어 mocking (Spring Boot 3.4+ 스타일)
- H2 DB 왕복이 필요한 경우 `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`로 별도 클래스 작성

**필수 테스트 케이스**:

| 케이스 | HTTP 상태 | 검증 내용 |
|---|---|---|
| 정상 요청 (AGENCY JWT) | 200 | `Content-Type`이 `text/csv`, `Content-Disposition`에 파일명 포함 확인 |
| 정상 요청 (AGENCY JWT) | 200 | 응답 바디가 UTF-8 BOM으로 시작하는지 검증 |
| JWT 없음 | 401 | 인증 실패 |
| ENGINEER JWT로 요청 | 403 | 권한 없음 |
| month=0 요청 | 400 | 유효성 검사 실패 |
| 데이터 없는 월 | 200 | 빈 데이터 CSV (헤더는 존재) 반환 |

---

## 구현 시 주의사항

- CSV 생성 로직은 반드시 `SettlementCsvGenerator`로 분리한다. `SettlementService`에 CSV 직렬화 코드를 인라인으로 넣지 않는다.
- 반환 타입은 `ResponseEntity<byte[]>`를 사용하고, `Content-Type`은 `text/csv; charset=UTF-8`로 명시한다.
- 파일명의 month는 항상 2자리로 패딩한다 (`String.format("%02d", month)`).
- 기사 이름 등 문자열 값에 쉼표나 큰따옴표가 포함될 경우 RFC 4180 CSV 이스케이프 규칙을 적용한다 (값을 큰따옴표로 감싸고, 내부 큰따옴표는 `""` 이중 처리).
- 집계 쿼리는 `engineer-performance`, `monthly-summary` API의 Service 메서드를 재사용하여 중복 쿼리를 방지한다.
- 한글 주석으로 비즈니스 로직의 의도와 주의사항을 코드에 남긴다.
