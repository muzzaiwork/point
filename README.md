# 💰 무료 포인트 시스템 (API)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot 3.4.1](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![H2 Database](https://img.shields.io/badge/Database-H2-blue.svg)](http://www.h2database.com/)
[![Gradle 9.0.0](https://img.shields.io/badge/Gradle-9.0.0-blue.svg)](https://gradle.org/)

---

### 📑 과제 정보
- **과제 요구사항**: [📄 요구사항 문서 보기](docs/요구사항.md)
- **핵심 목표**: 
  > [!IMPORTANT]
  > **적립 단위(`pointKey` 단위)의 상태 변화와 그 이력의 일관성을 끝까지 맞추는 시스템**

---

## 📖 목차
1. [개발 환경](#1-개발-환경)
2. [빌드 및 실행 방법](#2-빌드-및-실행-방법)
3. [접속 정보](#3-접속-정보)
4. [요구사항 구현 및 설계](#4-요구사항-구현-및-설계)
5. [핵심 로직 상세](#5-핵심-로직-상세)
6. [아키텍처 구성](#6-아키텍처-구성)

---

## 🛠 1. 개발 환경
| 항목 | 기술 스택 |
| :--- | :--- |
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.4.1 |
| **Database** | H2 (In-memory) |
| **Build Tool** | Gradle 9.0.0 (Wrapper) |
| **API Doc** | Springdoc OpenAPI (Swagger) |

---

## 🚀 2. 빌드 및 실행 방법

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

---

## 🌐 3. 접속 정보

### 3.1 서비스 접속
- **Swagger UI**: [🔗 http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **API Base URL**: `http://localhost:8080/points`

### 3.2 데이터베이스 접속 (H2 Console)
애플리케이션 실행 후 웹 브라우저에서 아래 정보로 데이터베이스에 접속할 수 있습니다.
- **H2 Console URL**: [🔗 http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **JDBC URL**: `jdbc:h2:mem:billing`
- **User Name**: `sa`
- **Password**: (입력 없음)

---

## 📐 4. 요구사항 구현 및 설계

### 4.1 데이터베이스 설계 (ERD)
상세한 데이터베이스 설계 및 Mermaid 다이어그램은 아래 문서에서 확인할 수 있습니다.
- [📊 데이터베이스 설계 (ERD) 상세 문서](docs/erd.md)

### 4.2 API 명세 및 상세 설계
각 API의 상세한 동작 방식, 데이터 흐름, 비즈니스 규칙은 아래 문서를 통해 확인할 수 있습니다.

| 기능 | Endpoint | 상세 문서 |
| :--- | :--- | :--- |
| **포인트 적립** | `POST /points/accumulate` | [📝 상세보기](docs/api/accumulate.md) |
| **적립 취소** | `POST /points/accumulate/{pointKey}/cancel` | [📝 상세보기](docs/api/cancel-accumulation.md) |
| **포인트 사용** | `POST /points/use` | [📝 상세보기](docs/api/use.md) |
| **사용 취소** | `POST /points/use/{pointKey}/cancel` | [📝 상세보기](docs/api/cancel-usage.md) |

---

## 💡 5. 핵심 로직 상세

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

## 🏗 6. 아키텍처 구성
AWS 기반 아키텍처 구성도는 `docs/architecture.md` 파일을 통해 Mermaid 다이어그램으로 확인할 수 있습니다.
- [☁️ AWS 아키텍처 상세 보기 (EKS, ALB, Aurora)](docs/architecture.md)
