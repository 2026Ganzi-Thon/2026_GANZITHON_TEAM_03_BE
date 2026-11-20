# 폴리의 섬 · Backend

나를 돌보는 작은 행동이 서로를 위한 온기로 이어지는 마음 돌봄 웹앱입니다. 짧은 감정 기록, 작은 자기 돌봄, 비동기 익명 위로와 휴식을 제공합니다.

2026 GANZITHON TEAM 03의 백엔드 저장소이며, 2026년 11월 21일 해커톤 시연·제출을 위한 프로젝트입니다. 정신 질환의 진단·치료를 대신하지 않습니다. 실제 결제·구독 처리는 구현 범위에 포함하지 않습니다.

## 서비스 및 API

| 구분 | 주소 |
| --- | --- |
| 프론트 웹앱 | https://2026-ganzithon-team-03-fe.vercel.app |
| 백엔드 HTTPS | https://3.38.62.168 |
| API 기본 경로 | https://3.38.62.168/api/v1 |
| Swagger UI | https://3.38.62.168/swagger-ui/index.html |
| OpenAPI JSON | https://3.38.62.168/v3/api-docs |
| 서버 상태 | https://3.38.62.168/actuator/health |
| 프론트 저장소 | https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_03_FE |

백엔드 주소는 API 서버입니다. 서비스 화면은 프론트 주소에서 확인합니다. Swagger는 `SWAGGER_ENABLED` 설정에 따라 비활성화할 수 있습니다. 배포 주소는 시연 환경 기준이며 이후 변경될 수 있습니다.

## 주요 기능

| 영역 | 구현 내용 |
| --- | --- |
| 회원·인증 | 이메일 인증 회원가입, 일반 로그인, Google 로그인, 비밀번호 재설정, 토큰 재발급, 로그아웃 |
| 프로필·동의 | 내 정보 조회·수정, 닉네임 중복 확인, 초기 프로필·온보딩, 약관 동의 관리 |
| 양 먹이 주기 | 복수 감정 선택, 선택적 메모, 비공개 감정 기록 조회·수정·삭제 |
| 우유와 치즈 | 익명 고민 작성·조회, 위로 방식 선택, 비동기 위로, 숙성·보관함 |
| 양털 모으기 | 분류별 돌봄 행동, 완료·취소, 양털 잔액·내역 |
| 섬 꾸미기 | 아이템 조회·구매, 보유 여부, 양털 차감 |
| 동굴 문 닫기 | 휴식 세션 시작·조회·종료 등 타이머 연동 API |

휴대폰 방향 감지와 타이머 화면은 프론트에서 처리합니다. 웹앱이 다른 앱이나 기기 전체 알림을 직접 차단하지는 않습니다.

## 기술 및 운영 환경

| 항목 | 구성 |
| --- | --- |
| 언어·프레임워크 | Java 21, Spring Boot 3.5.16 |
| 빌드 | Gradle 8.14.3 Wrapper, Groovy DSL |
| DB | MySQL, Spring Data JPA·JDBC, Flyway |
| 인증 | Spring Security, HS256 JWT, Google Identity Services |
| 메일 | Spring Mail, SMTP·STARTTLS |
| API 문서 | springdoc-openapi |
| 테스트 | JUnit 5, Spring Boot Test, MockMvc, MySQL 통합 테스트 |
| 배포 | AWS 서울 리전 EC2, 비공개 RDS MySQL 8.4, Nginx, systemd |
| CI/CD | GitHub Actions → OIDC → S3 → SSM → EC2 |
| 개발 도구 | macOS Apple Silicon, IntelliJ IDEA, Codex Desktop |

EC2는 HTTPS 요청을 Nginx에서 받아 내부 Spring Boot 프로세스로 전달합니다. 운영 비밀값은 EC2 실행 환경에서 관리하며 저장소에 포함하지 않습니다.

## 로컬 실행

Java 21과 MySQL 서버가 필요합니다. IntelliJ 설정은 [개발 환경 안내](docs/intellij-setup.md)를 참고합니다.

### DB 없이 기동 확인

```sh
./gradlew build
./gradlew bootRun --args='--spring.profiles.active=bootstrap'
```

`http://localhost:8080/actuator/health`에서 `{"status":"UP"}`을 확인합니다. 기본 `bootstrap` 프로필은 기동 확인용이며 업무 API와 DB를 제공하지 않습니다.

### 실제 기능 실행

1. 전용 MySQL 스키마와 계정을 준비합니다. 기존 운영 DB를 로컬 개발에 연결하지 않습니다.
2. [.env.example](.env.example)을 참고해 IntelliJ 실행 구성 또는 셸에 환경변수를 지정합니다.
3. 다음 명령으로 실행합니다.

```sh
./gradlew bootRun --args='--spring.profiles.active=mysql,auth,smtp'
```

Spring Boot는 `.env`를 자동으로 읽지 않습니다. 실제 값을 Git에 올릴 실행 구성 XML이나 설정 파일에 작성하지 않습니다.

| 환경변수 | 용도 |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 접속 |
| `JWT_SECRET_BASE64` | 32바이트 이상 무작위 서명 키의 Base64 값 |
| `GOOGLE_CLIENT_ID` | 프론트와 동일한 Google 웹 클라이언트 ID |
| `FRONTEND_ORIGIN` | 프론트 출처 및 메일 링크의 기본 주소 |
| `CORS_ALLOWED_ORIGINS` | 여러 허용 출처를 지정할 때 사용 |
| `AUTH_COOKIE_SAME_SITE` | 프론트·백엔드 배치에 맞는 쿠키 정책 |
| `TERMS_VERSION`, `PRIVACY_VERSION`, `MARKETING_VERSION` | 프론트와 맞출 문서 버전, 현재 시연은 `v1` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | 실제 메일 발송 |
| `EMAIL_VERIFICATION_FRONTEND_URL`, `PASSWORD_RESET_FRONTEND_URL` | 기본 메일 링크 경로를 변경할 때 사용 |

Flyway가 버전별 SQL을 적용하고 Hibernate `ddl-auto=validate`가 스키마를 검증합니다. 이미 공유·적용된 마이그레이션은 수정하지 않고 새 버전 파일을 추가합니다.

## 인증 연동 기준

- Access Token은 1시간 유효하며 프론트 메모리에 저장합니다. API 호출에 `Authorization: Bearer {accessToken}`을 사용합니다.
- Refresh Token은 HttpOnly·Secure 쿠키로 전달하고 서버에는 해시로 저장합니다. 재발급마다 회전하며 새 발급 시점부터 14일 유효합니다.
- 현재 배포 환경은 서로 다른 사이트의 프론트·백엔드 연동을 위해 `SameSite=None; Secure`를 사용합니다.
- Google 로그인은 프론트 GIS의 ID Token을 서버에서 검증합니다. Google Client Secret은 사용하지 않습니다.
- Google challenge는 15분 유효하며 로그인 성공 시 소비합니다. 신규 약관 동의 중에는 같은 credential과 challenge를 유지합니다.
- 회원 이메일은 앞뒤 공백 제거·소문자로 정규화합니다. 비밀번호는 원문 공백을 제거하지 않으며 8~64자·대문자·숫자·특수문자를 요구합니다.
- 닉네임은 한글·영문·숫자·밑줄 2~8자이며 대소문자를 구분합니다.

상세 요청·응답은 Swagger와 [Google 인증 연동 문서](docs/google-login.md)를 참고합니다. Google ID Token·서비스 토큰·쿠키·실제 비밀번호를 이슈나 로그에 공유하지 않습니다.

## 테스트

```sh
# 빌드·단위 테스트
./gradlew clean build

# 전용 로컬 테스트 DB의 환경변수를 지정한 뒤 실행
./gradlew mysqlIntegrationTest
```

MySQL 통합 테스트에는 `TEST_MYSQL_URL`, `TEST_MYSQL_USERNAME`, `TEST_MYSQL_PASSWORD`가 필요합니다. URL은 `localhost` 또는 `127.0.0.1`의 `maeum_member_test`로 시작하는 전용 DB만 허용합니다. 테스트가 데이터를 삭제하므로 서비스 DB를 사용하지 않습니다. CI의 MySQL 테스트 서버 버전은 8.0.46입니다.

GitHub Actions는 `develop`·`main` 대상 PR과 두 브랜치의 push에서 단위·MySQL 통합 테스트를 실행합니다.

## 협업과 배포

```text
기능 브랜치 → PR → develop → 통합 검증 → PR → main → CI 성공 → EC2 자동 배포
```

- 기능 브랜치는 최신 `develop`에서 생성하고 `main`·`develop`에 직접 push하지 않습니다.
- `develop`에서는 CI 검증만 실행합니다. `main` 병합 후 테스트가 통과하면 자동 재배포합니다. 병합한 팀원과 관계없이 동일하게 동작합니다.
- 배포는 같은 EC2 서버를 사용합니다. 서버 재시작 중에는 잠시 요청이 실패할 수 있습니다.
- 배포 후 헬스 확인에 실패하면 이전 JAR로 복구를 시도합니다. DB 마이그레이션은 자동 롤백되지 않습니다.
- 검증한 제출 커밋은 `v1.0.0` 같은 태그와 GitHub Release로 기록합니다. 이후 수정은 새 커밋과 새 버전 태그로 관리합니다.

[협업 규칙](CONTRIBUTING.md) · [EC2 배포 구성](deploy/ec2/README.md) · [PR 양식](.github/pull_request_template.md)

## 팀 구성과 담당

전체 팀은 프론트엔드 2명, 백엔드 3명, 디자인·기획 1명으로 구성되어 있습니다.

| 담당 | 범위 |
| --- | --- |
| 권우진 / [kwj2004](https://github.com/kwj2004) | 회원·인증·프로필·동의, 백엔드 배포 및 CI/CD 구성 |
| 백엔드 팀 공동 개발 | 감정 기록, 돌봄·양털, 고민·위로, 휴식, 홈, 섬 꾸미기 |
| 프론트엔드 팀 | 웹앱 UI, Google GIS 연동, 센서·타이머, API 연동 |
| 디자인·기획 | 서비스 기획, 화면·세계관·시각 디자인 |

개별 변경의 작성자와 리뷰 이력은 저장소의 커밋·PR에서 확인할 수 있습니다.

## 상세 문서

- [회원 저장 기반과 정책](docs/member-foundation.md)
- [이메일 인증](docs/email-verification.md)
- [Google 로그인·토큰·프론트 연동](docs/google-login.md)
- [감정 기본 데이터](docs/emotion-catalog.md)
- [돌봄 기본 데이터와 분류](docs/care-catalog.md)
- [시연 환경 설정 안내](docs/pre-demo-checklist.md)
- [GitHub 저장소 설정 안내](docs/repository-setup.md)

기능 명세·API 합의·ERD는 팀 공유 Notion과 ERDCloud에서도 관리합니다. 상세 문서의 설정 예시는 각 개발 환경에 맞게 적용하며, 실제 비밀값은 공유 문서나 저장소에 기록하지 않습니다.
