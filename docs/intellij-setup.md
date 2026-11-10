# IntelliJ 실행 환경

## 고정한 개발 도구

- Java 21 (이 Mac: Homebrew OpenJDK 21)
- Spring Boot 3.5.16
- Gradle 8.14.3, Groovy DSL, 저장소의 Wrapper 사용
- 패키지 루트: `com.ganzithon.maeum` (생성 시 임시 명칭; 팀 공통 세팅 PR에서 공유)

## IntelliJ에서 열기

1. File → Open에서 이 프로젝트의 `build.gradle`을 선택하고 Gradle 프로젝트로 엽니다.
2. File → Project Structure → Project SDK에서 Java 21을 지정합니다.
3. 이 Mac의 JDK Home: `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
4. Settings → Build, Execution, Deployment → Build Tools → Gradle에서 Distribution은 Wrapper, Gradle JVM은 Project SDK(Java 21)로 지정합니다.
5. Gradle 동기화 후 `Backend - bootstrap (DB 없이 실행 확인)`을 실행합니다. 실행 구성이 보이지 않으면 `MaeumApplication.main()`을 실행합니다.
6. `http://localhost:8080/actuator/health`에서 `{"status":"UP"}`을 확인합니다.

`.run`은 일반 Java Application 실행 구성이므로 Spring 전용 IDE 플러그인에 의존하지 않습니다. `.idea`는 개인 설정이며 커밋하지 않습니다.

## 프로필

| 프로필 | 용도 | DB |
| --- | --- | --- |
| bootstrap (기본) | 초기 빌드·기동·헬스 확인 | 연결하지 않음 |
| mysql | 회원·인증 API 구현용 | 팀이 준비한 MySQL 필요 |

bootstrap은 DB 없는 기동 확인 전용이며 이메일 인증 Controller·DB 서비스는 등록하지 않습니다. mysql 프로필에서 이메일 인증 발송·확인 API를 사용할 수 있습니다. SMTP 미설정은 503이며 가짜 발송 성공으로 처리하지 않습니다. 이메일 인증 POST 두 경로 외의 CSRF 보호는 유지합니다. JWT 인증·쿠키·배포 CORS 설정은 후속 구현 범위입니다.

## MySQL 사용 시

IntelliJ에서 bootstrap 실행 구성을 복제하고 프로그램 인자를 `--spring.profiles.active=mysql`로 바꿉니다. Environment variables에 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 지정합니다. 필요하면 `DB_HOST`, `DB_PORT`, `SERVER_PORT`도 지정합니다.

실제 메일 발송 시에는 `--spring.profiles.active=mysql,smtp`를 사용하고 [시연 전 설정 체크리스트](pre-demo-checklist.md)의 SMTP·인증 화면 주소를 환경변수로 지정합니다. 이 값들은 현재 미설정입니다.

Spring Boot는 `.env`를 자동으로 읽지 않습니다. `.env.example`을 참고해 IntelliJ 환경변수에 실제 값을 넣습니다. 비밀번호를 공유 실행 구성 XML이나 커밋할 설정 파일에 쓰지 않습니다.

회원 저장 기반은 Flyway V1으로 `users`, `local_credentials`를 생성하고 Hibernate `ddl-auto=validate`로 검증합니다. 전용 빈 스키마에서 시작하며 기존 수동 생성 DB를 임의로 baseline 처리하지 않습니다. [회원 저장 기반 안내](member-foundation.md)를 참고합니다.

## 터미널

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
./gradlew bootRun --args='--spring.profiles.active=bootstrap'
```

시스템 전역 Java 설정은 수정하지 않습니다. 다른 팀원은 자신의 Java 21 경로를 사용합니다.

## 개발 범위

- `auth`: 일반 가입·로그인, 구글 로그인, 이메일 인증, 비밀번호 재설정, 토큰 재발급·로그아웃
- `user`: 내 정보·초기 프로필, 닉네임, 온보딩, 동의 관리
- `global/config`: 공통 프레임워크 설정

컨트롤러·서비스·저장소는 각 기능 구현 시 도메인 안에 추가합니다. 이번 세팅에서는 미확정 JWT 키 방식, Refresh Token 저장 구조, Google 전달 방식, 메일 제공자, DB 마이그레이션 도구를 확정하지 않았습니다.

## GitHub에 올리기 전

팀 저장소는 `https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_03_BE.git`입니다. 공통 초기 세팅은 main에 병합되었으며 그 커밋에서 develop을 만들었습니다. 새 기능은 최신 develop에서 브랜치를 생성하고, 기능 브랜치를 push한 뒤 develop 대상 PR로 검토·병합합니다. 연동·시연 검증 후 develop에서 main으로 PR을 올립니다. main·develop에 직접 push하지 않습니다.
