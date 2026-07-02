# NurseDuty

간호사 근무 보조 앱 (Android + iOS, standalone·100% 로컬·계정 없음). MyDuty식 월간 근무표에
하루 한 근무를 채우면 그 듀티의 알람·체크리스트가 따라온다. 라운딩 중 빠른 메모(병상 태그)도.

## 기능 (v1)
- **근무표** — 월 달력, 날짜 탭 → 듀티 배정(하루 한 근무)
- **듀티 = 프로필** — D/E/N/Off/Charge 프리셋, 프로필별 알람 세트 + 체크리스트 템플릿 편집
- **알람** — 절대 시각 + `dayOffset`(야간 자정 넘김), 롤링 윈도우 스케줄러로 iOS 64-pending 상한 회피
- **체크리스트** — 날짜별 체크 상태(어제 안 번짐)
- **빠른 메모** — 병상 태그(`1001:01`) + 자유 텍스트, 인박스 스와이프 완료
- **백업/복원** — JSON 내보내기/불러오기, 이번 달 통계

## 구조
- `Modules/NurseDutyModel/` — 공유 SwiftData 패키지(`@Model` + 순수 로직 + 테스트). 앱·향후 위젯·워치가 import.
- `App/` — XcodeGen iOS 앱(SwiftUI). `project.yml`에서 `.xcodeproj` 생성(gitignore됨).

## 빌드 / 테스트 / 실행
```sh
# 패키지 로직 테스트
cd Modules/NurseDutyModel && swift test

# 앱 빌드 + 시뮬레이터 실행 (App Group entitlement 때문에 ad-hoc 서명 필요)
# 주의: -sdk는 쓰지 말 것 — 모든 타깃을 iOS로 강제해 임베드된 watchOS 앱이 잘못 빌드됨.
#       -destination만 쓰면 각 타깃이 제 SDK(iOS/watchOS)로 빌드됨.
cd App && xcodegen generate
xcodebuild -project NurseDuty.xcodeproj -scheme NurseDuty \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .build \
  build CODE_SIGN_IDENTITY="-" CODE_SIGNING_ALLOWED=YES
xcrun simctl install booted .build/Build/Products/Debug-iphonesimulator/NurseDuty.app
xcrun simctl launch booted com.example.nurseduty.app

# 워치 앱(임베드본)을 워치 시뮬에 설치:
#   xcrun simctl install <watch-udid> .build/Build/Products/Debug-iphonesimulator/NurseDuty.app/Watch/NurseDutyWatch.app
```
DEBUG 검증 런치 인자: `--seed-demo`(한 달치+샘플 메모 시드, 권한팝업 skip), `--tab-memo`, `--open-settings`.

## Android (현재 주력 — 1b 핀테크 리디자인 적용)
`android/` 하위 Gradle 멀티모듈: `:domain`(순수 JVM 로직+테스트) · `:app`(Room+AlarmManager+Compose,
5탭: 홈/근무표/듀티/메모/지참약) · `:wear`(Wear OS 컴패니언+타일). Glance 위젯, 로테이션 일괄 입력,
듀티 편집 시트, ward-pillcheck WebView 탭 포함.

```sh
# JDK 21 필요 (brew install openjdk@21), SDK는 ~/Library/Android/sdk (android/local.properties)
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :domain:test :app:testDebugUnitTest   # 단위 테스트
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug :wear:assembleDebug # 디버그 APK
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleRelease                   # 릴리스(R8+서명)
```
- 릴리스 서명: `android/local.properties`에 `RELEASE_STORE_FILE/-PASSWORD/KEY_ALIAS/KEY_PASSWORD`
  (keystore는 `android/keystore/` — **gitignore됨, 분실 시 업데이트 영구 불가이므로 반드시 별도 백업**).
- applicationId `com.yuangunn.nurseduty` (폰·워치 동일 — 컴패니언 페어링 요건).
- 에뮬레이터: AVD `nd`(폰, android-35) / `ndwear`(워치, android-34 wear). 워치 프리뷰:
  `adb shell am start -n com.yuangunn.nurseduty/com.nurseduty.wear.WearActivity -e preview evening`
- CI: `.github/workflows/android.yml` — push마다 테스트+APK 아티팩트.

## 로드맵
Android가 iOS를 추월(1b 리디자인·5교대+차지·위젯/워치/타일·지참약). 다음: iOS 패리티 이전.
설계/검증 근거는 `~/nurse-duty-app-로드맵.md`, 감사 결과는 `~/nurse-duty-app-개선점-2026-07.md` 참고.
