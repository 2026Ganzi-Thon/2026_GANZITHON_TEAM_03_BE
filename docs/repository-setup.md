# GitHub 설정 및 개발 초기화 체크리스트

문서·템플릿은 PR 검토안입니다. 관리자 설정은 파일 추가만으로 적용되지 않으며 아래 체크리스트도 적용 완료를 의미하지 않습니다.

## 1. 관리자가 확인할 설정

저장소 Settings에서 권한·정책을 확인합니다. 이 구성 작업에 연결된 계정은 push 권한은 있지만 admin·maintain 권한은 없습니다.

### main·develop 보호

- [ ] main·develop 대상 규칙 또는 Ruleset 생성
- [ ] 병합에 PR 요구
- [ ] 작성자 외 최소 1명 승인 요구
- [ ] 리뷰 대화 해결 요구
- [ ] 강제 푸시·브랜치 삭제 제한
- [ ] 실제 빌드 CI가 만들어져 첫 실행에 성공한 뒤 해당 검사 이름을 필수로 등록
- [ ] 긴급 우회 권한·새 커밋의 재승인 정책을 팀과 합의

CI 워크플로(`.github/workflows/ci.yml`)의 상태 검사 이름은 `단위·MySQL 통합 테스트`입니다. 첫 실행이 성공한 뒤 관리자가 main·develop 보호의 필수 검사로 등록할 수 있습니다.

### PR 병합·템플릿

- [ ] Squash merge 사용 여부 합의
- [ ] 병합 후 작업 브랜치 자동 삭제 여부 합의
- [ ] 이 PR 병합 후 새 PR에 작성 양식이 표시되는지 확인
- [ ] 이슈 생성 시 기능·버그 양식이 보이는지 확인
- [ ] 실제 담당 GitHub ID를 확인한 뒤 CODEOWNERS 도입 여부 검토

### Secrets·Actions

- [ ] 실제 CI·배포에서 필요한 비밀값만 GitHub Secrets에 등록
- [ ] 워크플로 기본 권한은 필요한 최소 권한으로 설정
- [ ] 배포용 Environment와 승인 정책은 배포 방식 확정 후 구성
- [ ] 서비스 계정·DB 계정·JWT 키는 개발/테스트/배포 환경별 분리

## 2. Spring Boot 초기화 PR에서 결정할 것

- [x] Java 21·Spring Boot 3.5.16 (로컬 초기화 승인)
- [x] 빌드 도구: Gradle
- [x] Gradle 8.14.3 Wrapper·Groovy DSL
- [ ] MySQL 버전, 로컬 설치 또는 Docker 사용 여부
- [ ] 패키지 루트·도메인 경계·공통 코드 관리 방식
- [x] 환경변수 주입·프로필 선택·IntelliJ 실행 방법: docs/intellij-setup.md
- [ ] JWT 알고리즘·키 형식, Refresh Token 서버 저장 구조
- [ ] 쿠키 SameSite·Domain·Path 및 CORS·CSRF 정책
- [ ] DB 마이그레이션 도구·이름·적용 절차
- [ ] 프로필 이미지·이메일 발송·구글 로그인 방식
- [x] 로컬 실행·테스트·빌드 명령을 README에 추가

Gradle Wrapper의 gradlew·gradlew.bat·gradle/wrapper 파일들을 커밋합니다. gradlew에는 macOS/Linux 실행 권한을 설정합니다.

## 3. 실제 빌드 CI 추가 조건

기술 버전과 프로젝트 파일이 없는 상태에서 임의 버전의 Spring Boot 프로젝트나 항상 성공하는 빌드 검사를 만들지 않습니다. 초기화 직후 같은 PR 또는 다음 PR에서 아래를 추가합니다.

- [x] pull_request 대상 main·develop과 두 브랜치 push에서 실행
- [x] contents: read 등 최소 권한 명시
- [x] 확정된 Java 버전 설치
- [x] 저장소의 Wrapper로 빌드·테스트 실행
- [x] DB 의존 테스트에는 격리된 테스트 DB 준비
- [x] 외부 Google·메일 서비스가 없어도 테스트가 가능하도록 구성
- [x] 실패 시 실제로 워크플로가 실패하도록 설정
- [ ] 테스트 결과 확인 후 상태 검사 이름을 main·develop 보호에 등록

## 공식 안내

- [PR 템플릿](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository)
- [보호 브랜치 관리](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
