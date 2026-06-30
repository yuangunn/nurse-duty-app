# 실기기 설치 가이드 (무료 Apple ID)

iOS는 코드사이닝 없이 실기기 설치가 안 되고, 서명은 **본인 Apple 계정·기기**에 묶입니다.
무료 Apple ID로는 **App Group·시간민감알림 entitlement이 막혀** 메인 빌드(위젯·워치 포함)는 서명이
실패합니다. 그래서 **폰 코어 앱만** 무료 서명으로 설치하는 변형(`project-device.yml`)을 씁니다.

이 변형으로 검증 가능: **근무표 · 듀티 프로필 · 알람 · 체크리스트 · 빠른 메모.**
빠진 것: 위젯 · 잠금화면 · 애플워치 (App Group/유료 계정 필요 → 메인 `project.yml` + TestFlight).

## 준비물
- 이 저장소를 받은 Mac (Xcode 26+)
- USB 케이블 + 아이폰
- 무료 Apple ID (Xcode → Settings → Accounts 에 추가돼 있어야 함)

## 단계
1. 프로젝트 생성:
   ```sh
   cd App
   xcodegen generate --spec project-device.yml
   open NurseDutyDevice.xcodeproj
   ```
2. Xcode에서 **NurseDuty** 타깃 선택 → **Signing & Capabilities** 탭:
   - **Automatically manage signing** 체크
   - **Team**: 본인 **Personal Team**(Apple ID) 선택
   - **Bundle Identifier**: 고유값으로 변경 — 예 `com.본인이름.nurseduty`
     (기본값 `com.example.nurseduty.device`는 이미 등록돼 있을 수 있음)
3. 아이폰을 USB로 연결 → 폰에서 "이 컴퓨터를 신뢰" 누름
4. Xcode 상단 실행 대상(destination)을 **본인 아이폰**으로 선택
5. **⌘R**(Run)
6. 첫 실행 시 폰에서 신뢰 설정:
   **설정 → 일반 → VPN 및 기기 관리 → (본인 Apple ID) → "신뢰"**
   → Xcode에서 다시 Run 하거나 홈에서 앱 실행
7. 앱 첫 실행 시 **알림 권한 → 허용**

## 알람 실기기 테스트
- 듀티 탭에서 어떤 듀티에 **현재 시각 + 2~3분 뒤** 알람을 하나 추가
- 근무표에서 **오늘**에 그 듀티 배정
- 폰을 잠그고 기다리면 배너+소리로 알람이 옴
  (무료라 집중모드 돌파(.timeSensitive)는 안 되지만 일반 알림·소리는 정상)

## 주의
- 무료 프로비저닝은 **7일 후 만료** → 만료되면 Xcode에서 다시 Run
- 무료 계정은 한 번에 등록 가능한 App ID 수 제한이 있음(보통 충분)

## 유료 계정으로 업그레이드하면
메인 `project.yml`로 풀 기능(위젯·워치·App Group·시간민감알림) 빌드 →
TestFlight 업로드(링크 OTA 설치, 지인 공유) 또는 ad-hoc IPA + GitHub Release.
그 단계 준비물(ExportOptions·아카이브 스크립트·CI)은 요청 시 만들어 드립니다.
