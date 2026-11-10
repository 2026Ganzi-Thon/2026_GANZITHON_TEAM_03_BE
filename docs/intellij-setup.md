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

bootstrap에는 업무 API 구현이 없습니다. Spring Security는 health만 허용하고 다른 경로를 차단합니다. JWT 인증·쿠키·CORS·CSRF·오류 응답은 실제 API 구현 단계에서 명세에 맞춰 추가해야 합니다. 기본 CSRF 보호는 유지합니다.

## MySQL 사용 시

IntelliJ에서 bootstrap 실행 구성을 복제하고 프로그램 인자를 `--spring.profiles.active=mysql`로 바꿉니다. Environment variables에 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 지정합니다. 필요하면 `DB_HOST`, `DB_PORT`, `SERVER_PORT`도 지정합니다.

Spring Boot는 `.env`를 자동으로 읽지 않습니다. `.env.example`을 참고해 IntelliJ 환경변수에 실제 값을 넣습니다. 비밀번호를 공유 실행 구성 XML이나 커밋할 설정 파일에 쓰지 않습니다.

현재 DB 엔티티·스키마·마이그레이션은 생성하지 않았습니다. `ddl-auto=validate`이며 실행만으로 기존 DB를 생성·수정·삭제하지 않습니다. 실제 DB 연결 검증은 접속 정보와 스키마가 준비된 후 수행합니다.

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

팀 저장소는 `https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_03_BE.git`입니다. 초기 세팅은 `codex/backend-repository-setup` 브랜치에서 PR로 검토합니다. 세 명 모두 초기 세팅이 병합된 동일한 기반에서 기능 브랜치를 생성합니다. 각자의 기능을 `main`에 직접 push하지 않습니다.
