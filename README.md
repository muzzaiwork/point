# 무료 포인트 시스템 (API)

본 프로젝트는 무신사페이먼츠 Backend Engineer 과제 전형을 위해 개발된 무료 포인트 시스템 API입니다.

## 1. 개발 환경
- **Java**: 21
- **Framework**: Spring Boot 3.2.2
- **Database**: H2 (In-memory)
- **Build Tool**: Gradle 8.x

## 2. 빌드 및 실행 방법

### 2.1 빌드 방법
터미널에서 프로젝트 루트 디렉토리로 이동 후 아래 명령어를 실행합니다.
```bash
./gradlew clean build
```

### 2.2 실행 방법
빌드가 완료된 후 아래 명령어를 실행하여 애플리케이션을 구동합니다.
```bash
./gradlew bootRun
```
또는 생성된 jar 파일을 실행합니다.
```bash
java -jar build/libs/point-0.0.1-SNAPSHOT.jar
```

## 3. 요구사항 구현 내용

### 3.0 Swagger UI (API 문서)
- 어플리케이션 실행 후 아래 경로에서 API 문서를 확인하고 테스트할 수 있습니다.
- `http://localhost:8080/swagger-ui/index.html`

### 3.1 적립 (Accumulation)
- 1회 적립 가능 포인트: 1 ~ 100,000 포인트 (설정 파일 또는 DB를 통해 제어 가능하도록 설계)
- 개인별 최대 보유 가능 포인트 제한 적용 (설정 가능)
- 특정 적립 건이 어떤 주문에서 사용되었는지 1원 단위로 추적 가능
- 수기 지급 포인트 구분 식별 가능
- 만료일 설정 (최소 1일 ~ 최대 5년, 기본 365일)

### 3.2 적립 취소 (Accumulation Cancel)
- 특정 적립 건에 대해 취소 가능
- 이미 사용된 금액이 있는 경우 취소 불가

### 3.3 사용 (Usage)
- 주문 시에만 사용 가능 (주문번호 필수 기록)
- 수기 지급 포인트 우선 사용 -> 만료일이 짧게 남은 순서로 사용

### 3.4 사용 취소 (Usage Cancel)
- 사용한 금액 전체 또는 일부 취소 가능
- 사용 취소 시점에 만료된 포인트는 신규 적립 처리

## 6. API 상세 (예시)

### 6.1 적립
- **Endpoint**: `POST /api/points/accumulate`
- **Request Body**:
  ```json
  {
    "userId": "user1",
    "amount": 1000,
    "isManual": false,
    "expiryDays": 365
  }
  ```

### 6.2 사용
- **Endpoint**: `POST /api/points/use`
- **Request Body**:
  ```json
  {
    "userId": "user1",
    "orderNo": "ORD-100",
    "amount": 500
  }
  ```

### 6.3 사용 취소
- **Endpoint**: `POST /api/points/use/{pointKey}/cancel`
- **Request Body**:
  ```json
  {
    "amount": 500
  }
  ```

## 7. 핵심 로직 설명

- **포인트 사용 순서**: `ORDER BY p.isManual DESC, p.expiryDate ASC` 쿼리를 통해 관리자 수기 지급 포인트를 최우선으로, 그 다음 만료일이 임박한 순서로 자동 차감됩니다.
- **1원 단위 추적**: `PointUsageDetail` 테이블을 통해 하나의 사용 건이 어떤 적립 건들에서 얼마씩 차감되었는지 기록합니다.
- **만료 포인트 복구**: 사용 취소 시 `PointAccumulation.isExpired()`를 체크하여, 만료된 경우 `PointService.accumulate()`를 내부적으로 호출해 신규 적립 처리합니다.

## 8. 데이터베이스 설계 (ERD)
ERD는 `docs/erd.md` 파일을 통해 Mermaid 다이어그램으로 확인할 수 있습니다.
(파일 경로: [erd.md](docs/erd.md))

또한, 이미지 형태의 ERD는 `src/main/resources/erd.png`에 위치합니다.

## 9. 아키텍처 구성
AWS 기반 아키텍처 구성도는 `src/main/resources/architecture.png`에 위치합니다.
- **RDBMS**: Aurora MySQL (Multi-AZ)
- **Cache**: ElastiCache (Redis) - 빈번한 잔액 조회 성능 향상
- **Load Balancer**: Application Load Balancer
- **Computing**: Amazon ECS (Fargate)
