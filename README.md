# 💰 무료 포인트 시스템 (API)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot 3.4.1](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![H2 Database](https://img.shields.io/badge/Database-H2-blue.svg)](http://www.h2database.com/)
[![Gradle 9.0.0](https://img.shields.io/badge/Gradle-9.0.0-blue.svg)](https://gradle.org/)

---

### 💼 채용 포지션
- **Payments Platform Engineer**: [📄 채용 공고 및 안내 보기](docs/채용%20포지션.md)

### 📑 과제 정보
- **과제 요구사항**: [📄 요구사항 문서 보기](docs/요구사항.md)
- **핵심 목표**
  > **🎯 적립 단위(`pointKey`) 및 사용 단위(`orderNo`)의 상태 변화와 그 이력의 일관성을 끝까지 맞추는 시스템**

---

## 📖 목차
1. [빌드 및 실행 방법](#1-빌드-및-실행-방법)
2. [접속 정보](#2-접속-정보)
3. [데이터베이스 및 API 설계](#3-데이터베이스-및-api-설계)
4. [핵심 로직 상세](#4-핵심-로직-상세)
5. [시스템 설계 공통 사항](#5-시스템-설계-공통-사항)
6. [아키텍처 구성](#6-아키텍처-구성)
7. [멱등성 및 데이터 정합성 설계](#7-멱등성-및-데이터-정합성-설계)
8. [부록: 무신사 도메인 특화 설계 고찰](#8-부록-무신사-도메인-특화-설계-고찰)

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

## 📐 3. 데이터베이스 및 API 설계

### 3.1 데이터베이스 설계 (ERD)
상세한 데이터베이스 설계 및 Mermaid 다이어그램은 아래 문서에서 확인할 수 있습니다.
- [📊 데이터베이스 설계 (ERD) 상세 문서](docs/데이터베이스%20설계.md)

### 3.2 API 명세
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
    "expiryDays": 365,
    "orderNo": "ORD202604010001"
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
      "pointKey": "A1234"  // 사용된 주문 번호(orderNo) 반환
    }
  }
  ```

---

#### 4️⃣ 사용 취소 API
사용된 포인트의 전액 또는 일부를 취소합니다. 이미 만료된 포인트는 신규 적립 처리됩니다. 취소 성공 시 `cancelledAt`(취소 일시)이 기록됩니다.

- **Method**: `POST`
- **Path**: `/points/use/{orderNo}/cancel`
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

---

## 💡 4. 핵심 로직 상세
<details>
<summary>🔄 적립/사용 데이터 흐름 및 상태 변화 (펼치기)</summary>

### [적립] 처리 흐름
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant UserEntity as User (Entity)
    participant PointEntity as Point (Entity)
    participant DB

    Client->>Controller: 적립 요청 (userId, amount, orderNo, type Enum)
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

### [사용] 처리 흐름
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant DB
    participant UserEntity as User (Entity)
    participant PointEntity as Point (Entity)
    participant OrderEntity as Order (Entity)
    participant DetailEntity as PointUsageDetail (Entity)

    Client->>Controller: 포인트 사용 요청 (userId, amount, orderNo)
    Controller->>Service: use()
    Service->>DB: 사용자 조회 (Pessimistic Lock)
    DB-->>Service: User 객체 반환
    Service->>UserEntity: usePoint(amount)
    Service->>DB: 사용 가능한 포인트 목록 조회 (isManual DESC, expiryDate ASC)
    Service->>OrderEntity: Order 생성 (orderNo 식별자)
    loop 사용 금액 소진 시까지
        Service->>PointEntity: subtractAmount(subAmount)
        Service->>DetailEntity: PointUsageDetail 생성 (1원 단위 연결)
    end
    Service->>DB: 모든 엔티티 저장 & User 업데이트
    Service-->>Controller: orderNo 반환
```

### [사용 취소] 처리 흐름
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant DB
    participant OrderEntity as Order (Entity)
    participant DetailEntity as PointUsageDetail (Entity)
    participant PointEntity as Point (Entity)
    participant UserEntity as User (Entity)

    Client->>Controller: 사용 취소 요청 (orderNo, amount)
    Controller->>Service: cancelUsage()
    Service->>DB: Order(사용 마스터) 조회
    Service->>OrderEntity: cancel(cancelAmount)
    Service->>DB: 사용자 조회 (Pessimistic Lock)
    Service->>DB: 사용 상세 내역(Detail) 조회
    loop 남은 취소 금액 소진 시까지
        alt 만료 안 됨
            Service->>PointEntity: restore(subAmount)
        else 이미 만료됨
            Service->>PointEntity: 신규 적립 생성
        end
        Service->>DetailEntity: addCancelledAmount(subAmount)
    end
    Service->>UserEntity: addPoint(amount)
    Service->>DB: 모든 변경 사항 저장
```
</details>

- **✨ 포인트 사용 우선순위**: 
  - 관리자 수기 지급 포인트를 최우선(`isManual DESC`)으로 사용합니다.
  - 그 다음 만료일이 임박한 순서(`expiryDate ASC`)로 자동 차감됩니다.
- **🔍 1원 단위 이력 추적**: 
  - `PointUsageDetail` 테이블을 통해 하나의 사용 건이 어떤 적립 건들에서 얼마씩 차감되었는지 정밀하게 기록합니다.
- **♻️ 만료 포인트 자동 신규 적립**: 
  - 사용 취소 시점에 이미 만료된 포인트는 원본 복구가 아닌, 정책에 따라 유효기간이 넉넉한 신규 포인트로 자동 적립 처리됩니다.
- **🧪 시나리오 검증**: 
  - 요구사항 예시 시나리오에 따른 데이터 변화는 [🔍 시나리오 흐름 문서](docs/시나리오%20흐름.md)에서 확인할 수 있습니다.
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
AWS 기반 아키텍처 구성도는 `docs/아키텍처 구성.md` 파일을 통해 Mermaid 다이어그램으로 확인할 수 있습니다.
- [☁️ AWS 아키텍처 상세 보기 (EKS, ALB, Aurora)](docs/아키텍처%20구성.md)

---

## 🔒 7. 멱등성 및 데이터 정합성 설계
데이터 정합성을 보장하기 위한 멱등성 및 동시성 제어 전략은 아래 문서에서 확인할 수 있습니다.
- [🔐 멱등성 및 데이터 정합성 설계 상세 문서](docs/동시성%20제어.md)

---

## 📎 8. 부록: 무신사 도메인 특화 설계 고찰

본 과제에서는 핵심적인 포인트 비즈니스 로직과 데이터 정합성에 집중하였습니다. 실제 무신사와 같은 대규모 이커머스 환경에서 더욱 견고하고 확장 가능한 시스템을 구축하기 위해 다음과 같은 요소들을 추가로 고려할 수 있습니다.

### 적립 근거(Origin) 추적 및 확장성 있는 ERD 설계
무신사의 특성상 포인트는 단순히 '금액'만 적립되는 것이 아니라, 다양한 사용자 활동(주문 구매, 리뷰 작성, 등급 혜택, 이벤트 참여 등)에 근거하여 발생합니다. 이를 위해 `Point` 테이블은 어떤 근거에 의해 포인트가 생성되었는지 추적할 수 있는 구조가 필요합니다.

- **적립 원천 식별 (Origin Key)**: `orderNo`(주문 번호), `reviewId`(리뷰 ID), `userGrade`(사용자 등급 정책 ID) 등 각 도메인별 고유 식별자를 외래키 또는 논리적 키로 보유하여 적립의 근거를 명확히 합니다.
- **다양한 적립 정책 관리**: 등급별 구매 적립률(%), 상품 카테고리별 추가 적립금 등 복잡한 비즈니스 규칙을 `PointPolicy`와 같은 별도 테이블로 관리하고, 적립 시점에 적용된 정책 ID를 기록하여 사후 정산 및 데이터 분석에 활용할 수 있습니다.
- **확장 가능한 데이터 모델링**: 새로운 적립 수단이 추가되더라도 기존 스키마를 크게 변경하지 않도록 `originType` (ORDER, REVIEW, EVENT 등) 컬럼을 활용한 전략 패턴 기반의 모델 설계를 고려할 수 있습니다.
