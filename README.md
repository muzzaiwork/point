# 💰 무료 포인트 시스템 (API)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot 3.4.1](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![H2 Database](https://img.shields.io/badge/Database-H2-blue.svg)](http://www.h2database.com/)
[![Gradle 9.0.0](https://img.shields.io/badge/Gradle-9.0.0-blue.svg)](https://gradle.org/)

---

### 📑 과제 정보
- **과제 요구사항**: [📄 요구사항 문서 보기](docs/요구사항.md)
- **핵심 목표**
  > **🎯 적립 단위(`pointKey` 단위)의 상태 변화와 그 이력의 일관성을 끝까지 맞추는 시스템**

---

## 📖 목차
1. [빌드 및 실행 방법](#1-빌드-및-실행-방법)
2. [접속 정보](#2-접속-정보)
3. [요구사항 구현 및 설계](#3-요구사항-구현-및-설계)
4. [핵심 로직 상세](#4-핵심-로직-상세)
5. [시스템 설계 공통 사항](#5-시스템-설계-공통-사항)
6. [아키텍처 구성](#6-아키텍처-구성)

---

## 🚀 1. 빌드 및 실행 방법

### 1.1 빌드 방법
터미널에서 프로젝트 루트 디렉토리로 이동 후 아래 명령어를 실행합니다.
```bash
./gradlew clean build
```

### 1.2 실행 방법
빌드가 완료된 후 아래 명령어를 실행하여 애플리케이션을 구동합니다.
```bash
./gradlew bootRun
```
또는 생성된 jar 파일을 실행합니다.
```bash
java -jar build/libs/point-0.0.1-SNAPSHOT.jar
```

---

## 🌐 2. 접속 정보

### 2.1 서비스 접속
- **Swagger UI**: [🔗 http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **API Base URL**: `http://localhost:8080/points`

### 2.2 데이터베이스 접속 (H2 Console)
애플리케이션 실행 후 웹 브라우저에서 아래 정보로 데이터베이스에 접속할 수 있습니다.
- **H2 Console URL**: [🔗 http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **JDBC URL**: `jdbc:h2:mem:billing`
- **User Name**: `sa`
- **Password**: (입력 없음)

---

## 📐 3. 요구사항 구현 및 설계

### 3.1 데이터베이스 설계 (ERD)
상세한 데이터베이스 설계 및 Mermaid 다이어그램은 아래 문서에서 확인할 수 있습니다.
- [📊 데이터베이스 설계 (ERD) 상세 문서](docs/erd.md)

### 3.2 API 명세 및 상세 설계
모든 API는 아래와 같은 일관된 공통 응답 구조를 가집니다.

#### [공통 응답 형식]
```json
{
  "code": "SUCCESS",       // 응답 코드 (SUCCESS, BAD_REQUEST, POINT_SHORTAGE 등)
  "message": "성공",       // 응답 메시지
  "data": { ... }          // 실제 응답 데이터 (없을 경우 null)
}
```

---

#### 1️⃣ 포인트 적립 API
사용자에게 포인트를 적립하며, 1회 최대 적립 한도 및 총 보유 한도를 검증합니다.

- **Method**: `POST`
- **Path**: `/points/accumulate`
- **Request Body**:
  ```json
  {
    "userId": "user1",
    "amount": 1000,
    "isManual": false,
    "type": "FREE",
    "expiryDays": 365
  }
  ```
- **Response (Success)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "적립 성공",
    "data": {
      "pointKey": "20260331000001"
    }
  }
  ```

<details>
<summary>🔄 데이터 흐름 및 상태 변화 (펼치기)</summary>

##### 처리 흐름 (Sequence Diagram)
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant UserEntity as User (Entity)
    participant PointEntity as Point (Entity)
    participant DB

    Client->>Controller: 적립 요청 (userId, amount, type Enum)
    Controller->>Service: accumulate()
    Service->>DB: 사용자 조회 (Pessimistic Lock)
    DB-->>Service: User 객체 반환
    Service->>UserEntity: addPoint(amount)
    Note over UserEntity: 1회 한도 및<br/>총 보유 한도 검증
    UserEntity-->>Service: 검증 완료 및 잔액 업데이트
    Service->>PointEntity: Point 객체 생성
    Service->>DB: Point 저장 & User 업데이트
    Service-->>Controller: pointKey 반환
    Controller-->>Client: 성공 응답
```

##### 케이스별 데이터 변화 예시
| 테이블 | 필드 | 변경 전 | 변경 후 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **USER** | `totalPoint` | `5,000` | `6,000` | 전체 잔액 1,000P 증가 |
| **POINT** | (신규 추가) | - | `amount: 1000` | 새로운 적립 레코드 생성 |

##### 주요 비즈니스 규칙
1. **최소 금액**: 1회 적립 시 최소 1포인트 이상이어야 합니다.
2. **1회 최대 한도**: `User` 엔티티에 설정된 `maxAccumulationPoint`를 초과하여 적립할 수 없습니다.
3. **총 보유 한도**: 적립 후 사용자의 `totalPoint`가 `maxRetentionPoint`를 초과할 경우 적립이 거부됩니다.
4. **동시성 제어**: 적립 처리 시 `User` 레코드에 비관적 락(`PESSIMISTIC_WRITE`)을 걸어 안전하게 잔액을 업데이트합니다.
</details>

---

#### 2️⃣ 적립 취소 API
적립된 포인트 전액을 취소합니다. 이미 사용된 포인트가 있는 경우 취소할 수 없습니다.

- **Method**: `POST`
- **Path**: `/points/accumulate/{pointKey}/cancel`
- **Request Body**: (Path Variable 사용)
- **Response (Success)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "적립 취소 성공",
    "data": null
  }
  ```

<details>
<summary>🔄 데이터 흐름 및 상태 변화 (펼치기)</summary>

##### 처리 흐름 (Sequence Diagram)
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant DB
    participant PointEntity as Point (Entity)
    participant UserEntity as User (Entity)

    Client->>Controller: 적립 취소 요청 (pointKey)
    Controller->>Service: cancelAccumulation(pointKey)
    Service->>DB: Point 정보 조회
    DB-->>Service: Point 객체 반환
    Service->>DB: 사용자 조회 (Pessimistic Lock)
    DB-->>Service: User 객체 반환
    Service->>PointEntity: cancel()
    Note over PointEntity: 1. 이미 취소되었는지 검증<br/>2. 일부라도 사용되었는지 검증
    PointEntity-->>Service: isCancelled=true, remainingAmount=0 업데이트
    Service->>UserEntity: usePoint(cancelAmount)
    UserEntity-->>Service: 사용자 잔액 업데이트 완료
    Service->>DB: Point & User 저장
    Service-->>Controller: 성공 반환
    Controller-->>Client: 성공 응답
```

##### 케이스별 데이터 변화 예시
| 테이블 | 필드 | 변경 전 | 변경 후 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **POINT** | `isCancelled` | `false` | `true` | 취소 완료 |
| **POINT** | `remainingAmount` | `1,000` | `0` | 해당 적립 건의 잔액 0 |
| **USER** | `totalPoint` | `6,000` | `5,000` | 전체 잔액에서 회수 |

##### 주요 비즈니스 규칙
1. **사용 여부 확인**: 적립된 금액 중 일부라도 사용된 경우( `remainingAmount < amount` ) 적립 취소가 불가능합니다.
2. **전체 잔액 반영**: 취소 시 사용자의 `totalPoint`에서 취소되는 적립 건의 잔액만큼 차감합니다.
3. **동시성 제어**: 사용자 레코드에 비관적 락을 획득하여 데이터 정합성을 보장합니다.
</details>

---

#### 3️⃣ 포인트 사용 API
주문에 필요한 포인트를 차감하며, 관리자 수기 포인트 및 만료 임박 포인트가 우선적으로 사용됩니다.

- **Method**: `POST`
- **Path**: `/points/use`
- **Request Body**:
  ```json
  {
    "userId": "user1",
    "orderNo": "A1234",
    "amount": 500
  }
  ```
- **Response (Success)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "사용 성공",
    "data": {
      "pointKey": "20260331000002"
    }
  }
  ```

<details>
<summary>🔄 데이터 흐름 및 상태 변화 (펼치기)</summary>

##### 처리 흐름 (Sequence Diagram)
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant DB
    participant UserEntity as User (Entity)
    participant PointEntity as Point (Entity)
    participant UsageEntity as PointUsage (Entity)
    participant DetailEntity as PointUsageDetail (Entity)

    Client->>Controller: 포인트 사용 요청 (userId, amount)
    Controller->>Service: use()
    Service->>DB: 사용자 조회 (Pessimistic Lock)
    DB-->>Service: User 객체 반환
    Service->>UserEntity: usePoint(amount)
    Service->>DB: 사용 가능한 포인트 목록 조회 (isManual DESC, expiryDate ASC)
    Service->>UsageEntity: PointUsage 생성
    loop 사용 금액 소진 시까지
        Service->>PointEntity: subtractAmount(subAmount)
        Service->>DetailEntity: PointUsageDetail 생성 (1원 단위 연결)
    end
    Service->>DB: 모든 엔티티 저장 & User 업데이트
    Service-->>Controller: 사용 pointKey 반환
```

##### 케이스별 데이터 변화 예시
| 테이블 | 필드 | 변경 전 | 변경 후 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **USER** | `totalPoint` | `1,500` | `300` | 전체 잔액 차감 (1,200P 사용 시) |
| **POINT (B)** | `remainingAmount` | `500` | `0` | **1순위**: 수기 포인트 소진 |
| **POINT (A)** | `remainingAmount` | `1,000` | `300` | **2순위**: 일반 포인트 차감 |

##### 주요 비즈니스 규칙
1. **사용 우선순위**: 1순위 관리자 수기 지급 포인트, 2순위 만료일 임박 순서로 차감됩니다.
2. **잔액 검증**: 사용자의 `totalPoint`가 요청 금액보다 적으면 즉시 실패 처리합니다.
3. **추적성**: `PointUsageDetail`에 어느 적립 건에서 얼마가 차감되었는지 1원 단위까지 기록합니다.
</details>

---

#### 4️⃣ 사용 취소 API
사용된 포인트의 전액 또는 일부를 취소합니다. 이미 만료된 포인트는 신규 적립 처리됩니다.

- **Method**: `POST`
- **Path**: `/points/use/{pointKey}/cancel`
- **Request Body**:
  ```json
  {
    "amount": 500
  }
  ```
- **Response (Success)**:
  ```json
  {
    "code": "SUCCESS",
    "message": "사용 취소 성공",
    "data": null
  }
  ```

<details>
<summary>🔄 데이터 흐름 및 상태 변화 (펼치기)</summary>

##### 처리 흐름 (Sequence Diagram)
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant DB
    participant UsageEntity as PointUsage (Entity)
    participant DetailEntity as PointUsageDetail (Entity)
    participant PointEntity as Point (Entity)
    participant UserEntity as User (Entity)

    Client->>Controller: 사용 취소 요청 (pointKey, amount)
    Controller->>Service: cancelUsage()
    Service->>DB: PointUsage(사용 마스터) 조회
    Service->>UsageEntity: cancel(cancelAmount)
    Service->>DB: 사용자 조회 (Pessimistic Lock)
    Service->>DB: 사용 상세 내역(Detail) 조회
    loop 남은 취소 금액 소진 시까지
        alt 만료 안 됨
            Service->>PointEntity: addRemainingAmount(subAmount)
        else 이미 만료됨
            Service->>PointEntity: 신규 적립 생성
        end
        Service->>DetailEntity: cancel(subAmount)
    end
    Service->>UserEntity: addPoint(amount)
    Service->>DB: 모든 변경 사항 저장
```

##### 케이스별 데이터 변화 예시
| 테이블 | 필드 | 변경 전 | 변경 후 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **USER** | `totalPoint` | `300` | `1,400` | 전체 잔액 1,100P 복구 |
| **POINT (B)** | `remainingAmount` | `0` | `400` | B에서 사용한 500P 중 400P 복구 (미만료) |
| **POINT (E)** | (신규 추가) | - | `amount: 700` | A에서 사용한 700P는 만료되어 신규 적립 |

##### 주요 비즈니스 규칙
1. **만료 처리**: 사용 취소 시점에 이미 만료된 포인트는 유효기간이 2999-12-31인 **신규 포인트로 적립**됩니다.
2. **부분 취소**: 전체 금액이 아닌 일부 금액만 취소할 수 있으며, 여러 번에 나누어 취소도 가능합니다.
3. **추적성 유지**: 어느 적립 건이 복구되거나 신규 적립되었는지를 `PointUsageDetail`을 통해 관리합니다.
</details>

---

## 💡 4. 핵심 로직 상세

- **✨ 포인트 사용 우선순위**: 
  - 관리자 수기 지급 포인트를 최우선(`isManual DESC`)으로 사용합니다.
  - 그 다음 만료일이 임박한 순서(`expiryDate ASC`)로 자동 차감됩니다.
- **🔍 1원 단위 이력 추적**: 
  - `PointUsageDetail` 테이블을 통해 하나의 사용 건이 어떤 적립 건들에서 얼마씩 차감되었는지 정밀하게 기록합니다.
- **♻️ 만료 포인트 자동 신규 적립**: 
  - 사용 취소 시점에 이미 만료된 포인트는 원본 복구가 아닌, 정책에 따라 유효기간이 넉넉한 신규 포인트로 자동 적립 처리됩니다.
- **🧪 시나리오 검증**: 
  - 요구사항 예시 시나리오에 따른 데이터 변화는 [🔍 시나리오 흐름 문서](docs/scenario-flow.md)에서 확인할 수 있습니다.
  - 실제 동작은 [💻 시나리오 테스트 코드 (JUnit 5)](src/test/java/org/musinsa/payments/point/scenario/PointScenarioTest.java)를 통해 검증되었습니다.

---

---

## 🛠 5. 시스템 설계 공통 사항

### 5.1 예외 처리 방식 (Exception Handling)
- **`BusinessException`**: 비즈니스 로직 위반 시 발생하는 커스텀 예외입니다. `ResultCode`를 통해 에러 코드와 HTTP 상태 코드를 관리합니다.
- **`GlobalExceptionHandler`**: `@RestControllerAdvice`를 사용하여 모든 예외를 전역적으로 포착하고, 일관된 `ApiResponse` 형식으로 응답합니다.
  - 예상치 못한 서버 오류는 `500 Internal Server Error`로 처리하며 보안을 위해 상세 에러는 로그에만 남깁니다.

**코드 예시 (GlobalExceptionHandler.java):**
```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    log.warn("[EXCEPTION] BusinessException: {} - {} | Code: {}", 
            e.getResultCode(), e.getMessage(), e.getResultCode().getCode());
    ResultCode resultCode = e.getResultCode();
    return ResponseEntity
            .status(resultCode.getHttpStatus())
            .body(ApiResponse.error(resultCode, e.getMessage()));
}
```

### 5.2 유효성 검증 방식 (Validation)
- **DTO 레벨 검증**: Jakarta Bean Validation(`@NotBlank`, `@Min`, `@NotNull` 등)을 사용하여 API 입력 단계에서 1차 검증을 수행합니다.
- **도메인 레벨 검증**: 엔티티 내부에서 비즈니스 규칙(예: 보유 한도 초과, 사용 금액 초과 등)을 직접 검증하여 데이터의 정합성을 보장합니다.

**코드 예시 (PointDto.java / User.java):**
```java
// DTO 검증
public static class AccumulateRequest {
    @NotNull(message = "적립 금액은 필수입니다.")
    @Min(value = 1, message = "적립 금액은 최소 1P 이상이어야 합니다.")
    private Long amount;
}

// 도메인 검증 (User.java)
public void addPoint(Long amount) {
    if (this.totalPoint + amount > this.maxRetentionPoint) {
        throw new BusinessException(ResultCode.LIMIT_EXCEEDED, "개인별 최대 보유 가능 포인트 초과");
    }
}
```

### 5.3 로깅 및 추적 방식 (Logging & Trace)
- **`ApiLoggingFilter`**: 모든 API의 요청(Method, URI, Body)과 응답(Status, Duration, Body)을 자동으로 로깅하여 이슈 발생 시 추적성을 확보합니다.
- **식별 키 기반 추적**: `pointKey`를 통해 적립-사용-취소로 이어지는 전체 라이프사이클을 추적할 수 있습니다.

**코드 예시 (ApiLoggingFilter.java):**
```java
private void logResponse(ContentCachingResponseWrapper response, long duration) {
    int status = response.getStatus();
    String payload = new String(response.getContentAsByteArray());
    log.info("[RESPONSE] Status: {} | Duration: {}ms | Body: {}", 
            status, duration, payload);
}
```

---

## 🏗 6. 아키텍처 구성
AWS 기반 아키텍처 구성도는 `docs/architecture.md` 파일을 통해 Mermaid 다이어그램으로 확인할 수 있습니다.
- [☁️ AWS 아키텍처 상세 보기 (EKS, ALB, Aurora)](docs/architecture.md)
