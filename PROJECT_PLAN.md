# 온길 (On-Gil) - 프로젝트 분석 및 향후 작업 계획서

> **슬로건**: "도로 위를 따뜻하게" (도로 위에 마음을 남깁니다)  
> **한 줄 정의**: 안전·매너운전과 도로 위 음성 쪽지로 나만의 펫을 키우고, 적립된 포인트를 현대자동차 실질 혜택으로 전환하는 참여형 모빌리티 일상 동행 서비스  
> **타깃 플랫폼**: 현대자동차그룹 Pleos Connect (Android Automotive OS, API 34 Landscape 2560×1440)

---

## 1. 프로젝트 현황 분석

### 1.1 서비스 컨셉 및 핵심 가치
1. **0점에서부터 쌓이는 긍정 강화형 안전·매너운전**
   - 100점에서 감점하는 스트레스형 점수가 아닌, 안전하고 배려하는 운전을 할 때마다 EXP와 포인트가 누적되는 구조.
   - 현대차 브랜드 컬러(현대 블루 `#002C5F`, 샌드 `#E4DCD3`, 액티브 블루 `#00AAD2`)로 통일된 고급스러운 인카 UI.
2. **도로 위 음성 쪽지 (10초 핸즈프리 & 익명 TTS)**
   - 주행 중에는 텍스트 입력을 차단하고 10초 음성(STT)으로만 작성.
   - 녹음 원본은 저장·공유하지 않고, AI 안전 필터링/순화 후 완전 익명 TTS로 전달.
   - FusedLocation 방위각(Heading) 기반으로 **진행 방향 ±45° 이내 운전자에게만 재생**하여 반대 차선 혼선 방지.
3. **애니메이션 마스코트 캐릭터 동행 (펫 '제로')**
   - 부드러운 셀 셰이딩·그라데이션, 초롱초롱한 듀얼 캐치라이트 눈망울, 살랑거리는 귀와 꼬리를 갖춘 **애니메이션 강아지 캐릭터 '제로' (`PetDog.kt`)**.
   - 성장 단계(Lv.1 기본 목걸이 → Lv.2 방울 머플러 → Lv.3 발그레한 볼터치 → Lv.4 황금 왕관)에 따른 시각적 변화.
   - 쪽지 수신 승인 시 **쪽지를 양손에 들고 경청하는 인터랙션** 제공.
4. **고정 시야 3D 내비게이션 (`NavMap.kt`)**
   - 주행 중 카메라 줌(`FIXED_CAMERA_ZOOM = 18.2`)과 기울기(`CAMERA_TILT = 45.0°`)를 고정하고, 3D 건물 압출 높이를 최대 `18m`로 상한 고정하여 고층 빌딩 구간이나 교차로 진입 시에도 시야가 출렁이거나 가려지지 않도록 설계.
   - `RouteWindow` 기반 슬라이딩 경로선 그라데이션 트리밍으로 차량이 지나간 경로를 실시간으로 부드럽게 소거.
5. **현대자동차 생태계 혜택 연계**
   - 적립된 온기 포인트를 블루멤버스(Blue Members), 블루핸즈 정비 할인권, 마이현대 Shop 순정용품 교환권, 현대해상 UBI 안전운전 특약 할인으로 전환.

---

### 1.2 아키텍처 및 구현 완성도

| 영역 | 구현 내용 | 상태 |
|---|---|---|
| **주행 화면 (DriveScreen)** | • 고정 시야(`18.2` 줌, 건물 최대 `18m` 캡) 3D 내비게이션 지도<br>• `RouteWindow` 기반 지나간 경로선 실시간 그라데이션 트리밍<br>• OSRM 실제 경로 파싱 및 오프라인 데모 경로 Fallback<br>• TBT 회전 안내 카드, 남은 시간/거리 표시<br>• 태양 위치 공식(NOAA) 기반 **주간(회청색) / 야간(어두운 톤) 지도 자동 전환**<br>• 주행 중 터치 차단(음성 승인 원칙 준수) 및 애니메이션 강아지 펫 '제로' 인터랙션 | **구현 완료** (검증 완료) |
| **정차/주차 화면 (ParkedScreen)** | • 기어 P 또는 정차(속도 0) 시 자동 전환되는 3-Pane 대시보드 (현대차 3색 시스템 적용)<br>• 받은 쪽지함 & 내가 남긴 쪽지함 (`NoteInboxScreen`)<br>• 설정 화면 (쪽지 자동 재생, 출퇴근 밴드 필터, 지도 테마 수동 선택)<br>• 오늘의 온기 리포트 ("N명이 들었고 M명이 좋아요")<br>• 나의 바퀴자취 지도 (익명 구간 단위 이동 경로 시각화)<br>• 현대 모빌리티 리워드 교환소 | **구현 완료** (검증 완료) |
| **안전·매너 평가 엔진 (MannerEvaluationEngine)** | • **Safe Drive**: 1.0km 정속 주행 시마다 +10 EXP / +5 P (10km마다 쪽지 작성권 +1장)<br>• **Smooth Drive**: 2.0km 구간 무급가감속 시 +20 EXP / +10 P 보너스 (급제동 시 감점 없이 해당 2km 보너스만 리셋)<br>• **Manner Drive**: 깜빡이 1.5초 유지 + 실제 방위각 25° 이상 회전/차선변경 + 45초 쿨타임 충족 시에만 인정 (**단순 점멸 어뷰징 완벽 차단**) | **구현 완료** (검증 완료) |
| **AI 쪽지 관리 (AiNoteModerator)** | • 비방/보복운전 위협 표현 즉시 차단 (`BLOCKED`)<br>• 차량번호(`12가 3456`), 연락처 자동 마스킹 및 불평 문구 다정 순화 (`PURIFIED`)<br>• 상황·안전(3시간), 따뜻한 응원(24시간), 감성·풍경(7일), 길·맛집 꿀팁(30일) 자동 수명 분류 | **구현 완료** (검증 완료) |
| **시연용 서랍 (SimulatorDrawer)** | • +0.5km 주행, 정석 차선변경, 깜빡이 어뷰징 테스트, 급제동 발생, 신호 대기, 음성 승인, 지도 주야간 전환 등 공모전 심사용 즉시 제어 | **구현 완료** (검증 완료) |
| **단위 테스트** | • `NoteBrowsingTest`, `PetLookTest`, `NavigationTest`, `RouteWindowTest`, `DayNightTest`, `MapThemeTest`, `CameraTweenTest`, `OsrmRouteParserTest` 등 총 67개 단위 테스트 통과 | **100% 통과** |

---

### 1.3 Pleos Connect 5대 SDK 연동 현황

1. **FusedLocation SDK (1.1.3)**
   - `LocationData` 수신 및 방위각 연동 완료.
   - `addProximityAlert` 반경 진입(ENTER) 및 이탈(EXIT) 감지 연동.
2. **NaviHelper SDK (3.0.5)**
   - 턴바이턴(TBTInfo), 제한속도(SpeedLimit), 현재 도로명(CurrentLocationInfo) 이벤트 연동.
   - 경로 상태(RouteStateInfo) 수신 연동.
3. **TextToSpeech SDK (3.3.1)**
   - Pleos On-Device TTS 우선 연동 및 안드로이드 시스템 TTS Fallback 체계 구축.
4. **SpeechToText SDK (3.3.1)**
   - STT 서비스 권한 및 `ko_KR` 로케일 요청 연동 완료.
5. **Vehicle SDK (2.3.2)**
   - Odometer, Brake, TurnSignal 인터페이스 연결 완료 (현재 에뮬레이터 환경 특성에 맞춰 하이브리드 시뮬레이터 연동 지원).

---

## 2. 남은 구현 및 단계별 작업 계획 (Work Plan)

공모전 출품 및 심사위원 시연 평가를 위한 단계별 실행 계획입니다.

```mermaid
flowchart TD
    Phase1["1단계: 시연 안정화 & 시각적 완성도 개선 (완료)"] --> Phase2["2단계: 차량 데이터 실제 연동"]
    Phase2 --> Phase3["3단계: 음성 입출력 & 서버 모드"]
    Phase3 --> Phase4["4단계: Gleo AI 온디바이스 연동"]
    Phase4 --> Phase5["5단계: 마감 및 공모전 출품 패키징"]
```

### 1단계: 시연 시나리오 안정화 및 시각적 완성도 개선 (완료)
- **목표**: 심사위원이 앱을 조작하거나 시연할 때 흔들림 없는 시야와 매력적인 캐릭터 경험 제공.
- **완료 내역**:
  1. **내비게이션 고정 시야 적용**: 교차로 거리별 가변 줌 제거(`FIXED_CAMERA_ZOOM = 18.2` 고정) 및 3D 빌딩 높이 상한(`18m`) 설정으로 시야 높낮이 변동 해소.
  2. **애니메이션 강아지 캐릭터 '제로' 리디자인**: 투박한 단색 도형에서 벗어나 극장판 애니메이션 풍의 크림/골든 강아지 일러스트 및 레벨별 성장 장식(목도리·볼터치·왕관) 적용.
  3. **지나간 경로선 실시간 소거(`RouteWindow`) 및 현대차 3색 테마 일원화**.

### 2단계: 차량 데이터(Vehicle SDK) 실제값 연동
- **목표**: 시뮬레이터 값을 넘어 실제 Pleos OS 차량 신호와 양방향 바인딩.
- **세부 작업**:
  1. **기어 상태 실시간 연동**: `Vehicle.getDrivingMode()` 또는 기어 리스너를 통해 실제 P/R/N/D 기어 변경 시 주행/정차 화면 자동 전환.
  2. **방향지시등 실제 신호 수신**: 차량 깜빡이 조작 신호 리스너 등록.
  3. **주행거리(Odometer) 실제 수신**: 차량 누적 주행거리 변화 감지.
  4. **차량 속도 산출**: SDK에 속도 읽기 API가 없을 경우, FusedLocation 좌표 변화량($\Delta d / \Delta t$)을 통한 정밀 실시간 속도 계산기 적용.

### 3단계: 음성 입출력(STT/TTS) 완성도 극대화
- **목표**: 핸즈프리 음성 경험의 현장 시연 완성.
- **세부 작업**:
  1. **실제 마이크 음성 입력 테스트**: 에뮬레이터 오디오 패스스루를 통한 실제 음성 인식 결과 검증.
  2. **STT/TTS 서버 모드(Client ID/Secret) 연동**: 사무국 공지 또는 발급 키 반영 (미발급 시 현재의 On-Device 모드 유지).
  3. **오디오 포커스 관리**: 차량 라디오 또는 음악 재생 중 쪽지 수신 시 부드러운 Ducking(소리 줄임) 처리.

### 4단계: Gleo AI 온디바이스 연동
- **목표**: 규칙 기반 AI 필터를 실제 차량 인포테인먼트 Gleo AI(`ai.pleos.playground:LLM`)로 승격.
- **세부 작업**:
  1. Pleos 저장소의 `LLM (2.1.3.2)` SDK 의존성 테스트.
  2. 에뮬레이터 내 `GleoAiModelProviderService` 호출 가능 여부 검증.
  3. 쪽지 순화 프롬프트 및 펫 맞춤 대사("오늘도 부드럽게 달려주셔서 마음이 편안해요") 생성 파이프라인 탑재.
  4. Gleo SDK 에뮬레이터 지원 불가 시, 현재의 경량 로컬 정규식/규칙 필터(`AiNoteModerator`)를 공식 백업으로 확정.

### 5단계: 최종 마감 및 출품 패키징
- **목표**: 현대자동차그룹 공모전 제출용 최종 산출물 완성.
- **세부 작업**:
  1. **차량 전조등(Headlamp) 신호 연동**: 차량 전조등 점등 시 낮/밤 지도 즉각 자동 전환.
  2. **APK 릴리즈 빌드 및 ProGuard 최적화**: 미사용 리소스 정리 및 안정적 배포 APK 생성.
  3. **시연 영상 녹화 및 대본 작성**: 2560×1440 와이드스크린 시연 녹화.
  4. **기획서 및 프레젠테이션(PPT) 문서 최종본 정렬**: 온길(On-Gil) 브랜딩 일치화.

---

## 3. 기술 스택 및 오픈소스 명세

- **OS & Framework**: Android Automotive OS (API 34), Jetpack Compose (BOM 2024.04.01)
- **Language**: Kotlin 2.0.0, Coroutines & Flow
- **Map & Terrain Engine**: MapLibre Native Android 11.13.5
- **Map Data**: OpenFreeMap 3D Vector Tiles, AWS Terrarium DEM (Hillshade)
- **Routing**: OSRM (Open Source Routing Machine) + 로컬 오프라인 데모 폴리라인
- **Astronomical Calculation**: NOAA Solar Position Formula (경위도 기반 정밀 주야간 자동 판정)
- **Vehicle Platform SDKs**:
  - `ai.pleos.playground:Vehicle:2.3.2`
  - `ai.pleos.playground:FusedLocation:1.1.3`
  - `ai.pleos.playground:NaviHelper:3.0.5`
  - `ai.pleos.playground:TextToSpeech:3.3.1`
  - `ai.pleos.playground:SpeechToText:3.3.1`
