# 무료 포인트 시스템 (API)

> ### 🎯 과제 목표
> **포인트 적립 단위(`pointKey` 단위)의 상태 변화와 그 이력의 일관성을 끝까지 맞추는 시스템**

## 1. 개발 환경
- **Java**: 21
- **Framework**: Spring Boot 3.4.1
- **Database**: H2 (In-memory)
- **Build Tool**: Gradle 9.0.0 (Wrapper)

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

### 2.3 접속 정보
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **API Base URL**: `http://localhost:8080/api/points`


## 3. 요구사항 구현 내용

### 3.0 데이터베이스 설계 (ERD)
상세한 데이터베이스 설계 및 Mermaid 다이어그램은 아래 문서에서 확인할 수 있습니다.
- [데이터베이스 설계 (ERD) 상세 보기](docs/erd.md)

### 3.1 API 명세 및 상세 설계

각 API의 상세한 동작 방식, 데이터 흐름, 비즈니스 규칙은 아래 문서를 통해 확인할 수 있습니다.

| 기능 | Endpoint | 상세 문서 |
| :--- | :--- | :--- |
| 포인트 적립 | `POST /api/points/accumulate` | [상세 보기](docs/api/accumulate.md) |
| 적립 취소 | `POST /api/points/accumulate/{pointKey}/cancel` | [상세 보기](docs/api/cancel-accumulation.md) |
| 포인트 사용 | `POST /api/points/use` | [상세 보기](docs/api/use.md) |
| 사용 취소 | `POST /api/points/use/{pointKey}/cancel` | [상세 보기](docs/api/cancel-usage.md) |

## 5. 핵심 로직 및 시나리오

- **포인트 사용 순서**: `ORDER BY p.isManual DESC, p.expiryDate ASC` 쿼리를 통해 관리자 수기 지급 포인트를 최우선으로, 그 다음 만료일이 임박한 순서로 자동 차감됩니다.
- **1원 단위 추적**: `PointUsageDetail` 테이블을 통해 하나의 사용 건이 어떤 적립 건들에서 얼마씩 차감되었는지 기록합니다.
- **만료 포인트 복구**: 사용 취소 시 `Point.isExpired()`를 체크하여, 만료된 경우 신규 적립 처리합니다.
- **시나리오 상세**: 요구사항 예시 시나리오에 따른 데이터 변화는 `docs/scenario-flow.md`에서 확인할 수 있습니다.
  - [시나리오 흐름 및 DB 상태 변화 상세보기](docs/scenario-flow.md)
  - [시나리오 테스트 코드 (JUnit 5)](src/test/java/org/musinsa/payments/point/scenario/PointScenarioTest.java)

## 6. 아키텍처 구성
AWS 기반 아키텍처 구성도는 `docs/architecture.md` 파일을 통해 Mermaid 다이어그램으로 확인할 수 있습니다.
- [AWS 아키텍처 상세 보기 (Mermaid)](docs/architecture.md)
