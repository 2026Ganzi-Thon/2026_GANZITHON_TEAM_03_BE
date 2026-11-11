# 2026 GANZITHON TEAM 03 · Backend

나를 돌보는 작은 행동이 서로를 위한 온기로 이어지는 마음 돌봄 웹앱의 백엔드 저장소입니다.

## 프로젝트 범위

- 양 먹이 주기: 비공개 감정 기록
- 우유와 치즈: 비동기 익명 고민·위로
- 양털 모으기: 작은 자기 돌봄과 양털 보상
- 동굴 문 닫기: 휴식 타이머와 모바일 웹 기기 방향 감지 연계

배포·시연 마감은 2026년 11월 21일입니다. 일반 사용자 대상 공개 운영과 실제 결제·구독 처리는 현재 범위에 포함하지 않습니다. 웹앱이 다른 앱이나 기기 전체 알림을 직접 차단하지는 않습니다.

## 현재 상태와 기술

회원 저장 기반과 이메일 인증 발송·확인 API를 구현했습니다. DB 없이 기동을 확인하는 bootstrap 프로필과 MySQL 연결용 프로필을 구분합니다. 실제 메일은 mysql,smtp 프로필과 SMTP 접속 정보가 필요합니다. Google 로그인·신규 가입 동의·JWT 인증·Refresh Token 회전·세션 로그아웃을 구현했습니다. Google 인증은 mysql,auth 프로필과 실제 설정이 필요합니다. 일반 이메일 회원가입·로그인도 `mysql,auth` 프로필에서 사용할 수 있습니다.

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
- MySQL 프로필은 Flyway로 버전별 SQL을 적용하고 Hibernate `ddl-auto=validate`로 검증합니다. 전용 빈 스키마에서 시작합니다.
- auth 프로필에서는 쿠키 인증 API에 허용 Origin 검증을 적용하고, 나머지 `/api/v1/**`는 Access Token을 요구합니다. auth 미사용 시 기존 제한을 유지합니다.
- JWT는 HS256, Refresh Token은 DB 해시 저장으로 구현했습니다. 실제 Google Client ID·JWT 키·Origin·약관 버전은 실행 환경에 주입합니다.
- 빌드 CI는 아직 구성하지 않았습니다. 공통 세팅은 PR에서 검토한 뒤 팀 공통 기반으로 병합합니다.

실제 비밀번호·JWT 키·토큰·OAuth 시크릿·감정 기록·고민 본문을 저장소·이슈·PR·로그에 넣지 않습니다.

## 협업 문서

기능은 최신 `develop`에서 브랜치를 만들고 `기능 브랜치 → develop` PR로 통합합니다. 연동·시연 검증 후 `develop → main` PR로 배포 버전을 반영합니다. 두 브랜치에 직접 push하지 않습니다.

- [회원 저장 기반·정책·MySQL 통합 테스트](docs/member-foundation.md)
- [개발·Git 협업 규칙 제안](CONTRIBUTING.md)
- [GitHub 관리자 설정 및 초기화 체크리스트](docs/repository-setup.md)
- [PR 작성 양식](.github/pull_request_template.md)
- API·기능 명세·ERD: 팀 공유 노션과 ERDCloud에서 관리합니다. 공개 저장소에 연결할 링크는 팀이 공유 범위를 확인한 뒤 추가합니다.

AGENTS.md는 요청에 따라 이번 구성에 포함하지 않습니다.
- [시연 전 직접 설정할 항목 — SMTP·Google·배포 주소·QR 연동](docs/pre-demo-checklist.md)
- [이메일 인증 API와 발송 방식](docs/email-verification.md)

- [Google 로그인 API·토큰·프론트 연동](docs/google-login.md)
