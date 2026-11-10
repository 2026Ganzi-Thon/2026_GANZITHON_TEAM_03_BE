# 회원 저장 기반

## 이번 범위

- `users`: 공통 회원 ID, 연락 이메일, 미완료 프로필·온보딩 상태 및 생성·수정 시각
- `local_credentials`: 회원별 일반 로그인 이메일·비밀번호 해시, 이메일 검증·비밀번호 변경 시각
- 한 회원당 일반 로그인 정보 최대 1개 (`user_id` PK·FK)
- 일반 로그인 이메일은 정규화 후 UNIQUE. 연락 이메일은 UNIQUE가 아니며 소셜 계정 자동 연결에 사용하지 않습니다.
- 회원과 일반 로그인 정보 저장은 한 트랜잭션입니다. 동시 중복에서 실패한 요청의 회원 행도 롤백됩니다.

`LocalMemberStore`는 저장 기반이며 공개 회원가입 API가 아닙니다. 인증 증명·약관·지갑·소셜 계정·토큰은 생성하지 않습니다. 이메일 인증 완료 시각도 임의로 채우지 않습니다. 후속 가입 서비스에서 선행 검증과 동의 저장을 포함한 전체 트랜잭션으로 호출해야 합니다.

## 2026-11-10 확정한 정책

- DB 변경은 Flyway SQL 파일로 관리합니다.
- 일반 로그인 이메일은 `strip()`으로 앞뒤 공백 제거 후 `Locale.ROOT` 소문자로 저장합니다. 최대 255자이며 이메일 형식을 검증합니다.
- 가입 비밀번호는 Unicode 코드 포인트 기준 8~64자, 영문 대문자·숫자·특수문자 포함입니다. 특수문자는 Unicode 문장부호(P)·기호(S)이며 공백만으로는 충족하지 않습니다.
- 원문을 trim·잘라내기·로그 기록하지 않습니다. 로그인 시 신규 가입 정책을 다시 강제하지 않습니다.
- PBKDF2-HMAC-SHA256, 무작위 salt 16바이트, 600,000회로 해시합니다. `{pbkdf2-sha256-600k}` 식별자를 함께 저장합니다. 해시 설정 변경 시 기존 식별자의 검증 설정을 보존해야 합니다.

참조한 [일반 회원가입 명세](https://app.notion.com/p/a5a2dc037c328246a3348122e8cb0915)에는 비밀번호 15~64자 제안이 남아 있습니다. 이번 사용자 확정값 8~64자를 코드에 적용했으며 노션 원문은 수정하지 않았습니다. [내 정보 조회 명세](https://app.notion.com/p/e312dc037c32826c9e4a018619b4faad)의 회원·프로필 필드를 저장 구조에 반영했습니다. 최신 ERD 원본은 연결된 자료에서 찾지 못했으므로 병합 전에 다른 도메인 담당자와 FK 대상·컬럼을 대조합니다.

## 마이그레이션

`mysql` 프로필에서 Flyway V1 적용 → Hibernate `validate` 순서로 시작합니다. V1은 `users`, `local_credentials`만 생성하며 기존 테이블을 삭제하거나 수정하지 않습니다. MySQL 8의 utf8mb4_0900_bin 및 CHECK 제약을 사용합니다. MySQL 8.0.46에서 검증했습니다.

전용 빈 스키마에서 시작합니다. 이미 수동 생성한 테이블이 있는 DB에 임의 적용하지 않습니다. `baseline-on-migrate=false`, `clean-disabled=true`를 유지합니다. 적용한 SQL 파일을 수정하지 않고 후속 버전 파일을 추가하며, 세 명이 새 버전 번호를 사전 공유합니다.

닉네임 30자는 저장 가능한 컬럼 용량이며 화면의 2~8자 정책을 확정한 것이 아닙니다. 아직 닉네임 변경·검증 기능은 없습니다. 나이대·지역 코드는 미정이므로 enum이나 필수 조건을 만들지 않았습니다.

## 테스트

```sh
./gradlew build
# 별도 로컬 테스트 DB를 미리 준비하고 아래 환경변수를 주입합니다.
export TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:13316/maeum_member_test?connectionTimeZone=UTC'
export TEST_MYSQL_USERNAME='전용 테스트 계정'
# TEST_MYSQL_PASSWORD는 환경변수로 안전하게 주입
./gradlew mysqlIntegrationTest
```

일반 `build`는 비밀번호·기동 테스트를 실행하며 MySQL 통합 테스트를 포함하지 않습니다. `mysqlIntegrationTest`는 localhost 또는 127.0.0.1의 `maeum_member_test`로 시작하는 DB 이름만 허용합니다. 테스트 중 회원 관련 두 테이블의 데이터를 정리하므로 반드시 폐기 가능한 전용 DB와 계정을 사용합니다. 환경변수가 없으면 성공으로 건너뛰지 않고 실패합니다.

통합 테스트는 실제 MySQL에서 Flyway 생성·JPA 검증·PK/FK 저장·정규화·DB 중복 제약·동시 중복 롤백을 확인합니다. 아직 HTTP 엔드포인트가 없어 공통 응답 DTO·ControllerAdvice는 추가하지 않았습니다. 다음 API PR에서 오류 형식을 연결하고, DB UNIQUE 충돌도 `AUTH_EMAIL_DUPLICATED`로 매핑해야 합니다.

## 다음 PR

이메일 인증 발송·확인·가입 증명을 구현한 뒤, 회원가입 API에서 본 저장 기반과 약관·지갑 저장을 통합합니다. JWT·Refresh Token 세션 구현은 별도 범위입니다.

해시 구현 참고: [Spring Security 비밀번호 저장](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html), [PBKDF2 설정 API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.html).
