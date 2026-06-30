# NurseDuty

간호사 근무 보조 앱 (iOS, standalone·100% 로컬·계정 없음). MyDuty식 월간 근무표에
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
cd App && xcodegen generate
xcodebuild -project NurseDuty.xcodeproj -scheme NurseDuty -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .build \
  build CODE_SIGN_IDENTITY="-" CODE_SIGNING_ALLOWED=YES
xcrun simctl install booted .build/Build/Products/Debug-iphonesimulator/NurseDuty.app
xcrun simctl launch booted com.example.nurseduty.app
```
DEBUG 검증 런치 인자: `--seed-demo`(한 달치+샘플 메모 시드, 권한팝업 skip), `--tab-memo`, `--open-settings`.

## 로드맵
v1(로컬) 완료. 다음: 위젯(WidgetKit) → Apple Watch(WatchConnectivity) → Android 포팅.
설계/검증 근거는 `~/nurse-duty-app-로드맵.md` 참고.
