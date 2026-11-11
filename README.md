# 온길 (On-Gil)

> **"도로 위를 따뜻하게"**  
> 안전·매너운전과 도로 위 쪽지 활동으로 나만의 펫을 키우고, 적립된 포인트를 현대자동차 실질 혜택으로 연결하는 참여형 모빌리티 일상 동행 서비스

현대자동차그룹 차량용 앱 개발 공모전 (Pleos Connect) 출품작입니다.

---

## 🌟 핵심 기능

1. **텍스트 최소화 3D 내비게이션 주행 화면**
   - 어두운 3D 입체 건물 지도 위로 주행 경로만 밝게 강조
   - 태양 고도(NOAA) 기반 **주간(회청색) / 야간(다크) 지도 자동 전환**
   - 시선 분산을 최소화한 턴바이턴(TBT) 안내 카드와 남은 시간/거리 표시

2. **부드러운 일러스트 펫 '제로(Zero)'**
   - 차량 내 실시간 동행 캐릭터
   - 성장 단계(Lv.1 기본 → Lv.2 목도리 → Lv.3 볼터치 → Lv.4 왕관)별 시각적 보상
   - 쪽지 수신 시 **쪽지를 양손에 들고 경청하는 애니메이션**

3. **도로 위 익명 음성 쪽지 (10초 핸즈프리)**
   - 주행 중 음성으로만 안전하게 작성 (텍스트 입력 차단)
   - AI 실시간 안전 검증, 개인정보(차량번호/연락처) 마스킹, 비방 차단 및 다정 순화
   - **진행 방향 ±45° 이내 운전자에게만 재생**하여 반대 차선 혼선 방지
   - 1-Tap '좋아요' 피드백 및 쪽지 수명 연장

4. **어뷰징 방지 3대 안전·매너운전 평가 엔진 (무감점 긍정 강화)**
   - **Safe Drive**: 1.0km 안정 주행 완주 시마다 +10 EXP / +5 P
   - **Smooth Drive**: 2.0km 무급가감속 완주 시 +20 EXP / +10 P (급제동 시 감점 없이 보너스만 리셋)
   - **Manner Drive**: 깜빡이 1.5초 유지 + 실제 방위각 25° 이상 회전/차선변경 + 45초 쿨타임 충족 시에만 인정 (**단순 점멸 어뷰징 차단**)

5. **주차(P) 대시보드 & 현대 모빌리티 리워드 연계**
   - 기어 P 또는 정차 시 쪽지함, 설정, 오늘의 온기 리포트 자동 활성화
   - 나의 바퀴자취 지도 (구간 단위 익명 주행 자취)
   - 블루멤버스(Blue Members), 블루핸즈 정비 할인권, 마이현대 Shop 포인트 교환

---

## 🛠️ 기술 스택

- **플랫폼**: Android Automotive OS (API 34, 2560×1440 Landscape)
- **UI 프레임워크**: Jetpack Compose (BOM 2024.04.01), Material 3
- **지도 엔진**: MapLibre Native Android 11.13.5 (OpenFreeMap 3D Vector Tiles + AWS Terrarium DEM Hillshade)
- **Pleos Connect SDKs**:
  - `ai.pleos.playground:Vehicle:2.3.2`
  - `ai.pleos.playground:FusedLocation:1.1.3`
  - `ai.pleos.playground:NaviHelper:3.0.5`
  - `ai.pleos.playground:TextToSpeech:3.3.1`
  - `ai.pleos.playground:SpeechToText:3.3.1`

---

## 📂 프로젝트 문서

- [프로젝트 분석 및 향후 작업 계획서 (PROJECT_PLAN.md)](PROJECT_PLAN.md)
