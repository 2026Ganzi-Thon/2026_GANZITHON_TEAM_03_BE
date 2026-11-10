# 2026 GANZITHON TEAM 03 · Backend

나를 돌보는 작은 행동이 서로를 위한 온기로 이어지는 마음 돌봄 웹앱의 백엔드 저장소입니다.

## 프로젝트 범위

- 양 먹이 주기: 비공개 감정 기록
- 우유와 치즈: 비동기 익명 고민·위로
- 양털 모으기: 작은 자기 돌봄과 양털 보상
- 동굴 문 닫기: 휴식 타이머와 모바일 웹 기기 방향 감지 연계

배포·시연 마감은 2026년 11월 21일입니다. 일반 사용자 대상 공개 운영과 실제 결제·구독 처리는 현재 범위에 포함하지 않습니다. 웹앱이 다른 앱이나 기기 전체 알림을 직접 차단하지는 않습니다.

## 현재 상태와 기술

회원·인증·프로필 개발을 위한 Spring Boot 프로젝트를 초기화했습니다. DB 없이 기동을 확인하는 bootstrap 프로필과 MySQL 연결용 프로필을 구분합니다. 업무 API와 JWT 인증 로직은 아직 구현하지 않았습니다.

| 항목 | 상태 |
| --- | --- |
| 개발 환경 | macOS Apple Silicon, IntelliJ IDEA, Codex Desktop |
| 협업 | 백엔드 3명, GitHub Feature Branch·PR |
| 언어·프레임워크 | Java 21·Spring Boot 3.5.16 (프로젝트 생성 승인 조합) |
| DB | MySQL 예정, 버전 미정 |
| 빌드 도구 | Gradle 8.14.3 Wrapper·Groovy DSL |
| 배포 환경 | 미정 |

## 실행 및 개발 준비

자세한 절차와 IntelliJ JDK 경로는 [IntelliJ 설정 안내](docs/intellij-setup.md)를 참고합니다.

```sh
# Java 21이 설정된 환경에서 실행
./gradlew build
./gradlew bootRun --args='--spring.profiles.active=bootstrap'
```

- 기동 확인: `http://localhost:8080/actuator/health`
- 기본 bootstrap 프로필은 DB를 연결하지 않습니다. 업무 API 개발 시 mysql 프로필과 실제 DB 환경변수를 지정합니다.
- `.env.example`은 필요한 환경변수 안내입니다. Spring Boot가 `.env`를 자동으로 읽지는 않습니다.
- MySQL 설정은 `ddl-auto=validate`이며 DB 스키마를 자동으로 변경하지 않습니다.
- 서버 상태 확인 외 API는 초기 보안 설정에서 차단합니다. 실제 인증·프로필 API는 별도 구현이 필요합니다.
- API 명세 미결정 사항, DB 구조, Refresh Token 저장소, JWT·Google·메일 방식은 구현 전에 확정해야 합니다.
- 빌드 CI는 아직 구성하지 않았습니다. 공통 세팅은 PR에서 검토한 뒤 팀 공통 기반으로 병합합니다.

실제 비밀번호·JWT 키·토큰·OAuth 시크릿·감정 기록·고민 본문을 저장소·이슈·PR·로그에 넣지 않습니다.

## 협업 문서

- [개발·Git 협업 규칙 제안](CONTRIBUTING.md)
- [GitHub 관리자 설정 및 초기화 체크리스트](docs/repository-setup.md)
- [PR 작성 양식](.github/pull_request_template.md)
- API·기능 명세·ERD: 팀 공유 노션과 ERDCloud에서 관리합니다. 공개 저장소에 연결할 링크는 팀이 공유 범위를 확인한 뒤 추가합니다.

AGENTS.md는 요청에 따라 이번 구성에 포함하지 않습니다.
