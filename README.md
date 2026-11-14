# 온길 (On-Gil)

운전자의 매너운전 습관과 도로 위에서 나누는 따뜻한 교류를 쌓아, 펫 성장과 차량 혜택으로 이어주는 차량용 일상 동행 서비스

## APK

- 위치: [`apk/온길_release.apk`](apk/온길_release.apk)
- release 빌드(서명됨), 패키지 `com.example.hyundai`, 버전 1.0.0

## 실행 방법

1. Pleos Connect 에뮬레이터를 실행합니다.
2. APK를 설치합니다.

   ```bash
   adb install -r apk/온길_release.apk
   ```

3. 앱 목록에서 "온길"을 실행합니다. 명령으로 실행하려면 다음을 씁니다.

   ```bash
   adb shell am start -n com.example.hyundai/.MainActivity
   ```

4. 처음 실행할 때 위치(FUSED_LOCATION 포함)와 마이크 권한을 허용합니다.

소스에서 직접 빌드하려면 JDK 17 또는 21과 Android SDK(compileSdk 35)가 필요합니다.

```bash
./gradlew :app:assembleDebug
```

결과물은 `app/build/outputs/apk/debug/app-debug.apk`입니다. release 서명용 `keystore.properties`는 저장소에 포함하지 않습니다.

## 테스트 환경 및 필요한 설정

- **인터넷 연결:** 경로(OSRM), 지도 타일(OpenFreeMap), 장소·충전소 검색(Nominatim)에 씁니다. 연결이 없으면 경로는 내장된 시연 경로로 대신합니다.
- **시연 주행:** GPS 위치가 경로에서 2km 넘게 떨어져 있으면(에뮬레이터 기본 위치 등) 앱이 경로를 따라 주행을 자동으로 진행합니다. 경로 근처로 옮기면(`adb emu geo fix <경도> <위도>`) 실제 위치를 따라갑니다.
- **기어·속도 조작:** 화면 하단의 시뮬레이터 조작판 또는 Pleos 가상 콘솔로 바꿉니다.
- **가상 콘솔(선택):** `adb forward tcp:8088 tcp:8088` 후 PC 브라우저에서 `http://127.0.0.1:8088/`을 엽니다. 기어, 속도, 방향지시등, 전조등, 배터리, 경고등, 크루즈, 하이패스 값을 바꾸면 앱이 1초마다 읽어 반영합니다. 설정의 "3D 도로" 주행 화면은 가상 콘솔이 켜진 에뮬레이터에서만 보입니다.
- **주행 중 조작 제한:** 쪽지함, 설정, 목적지 화면은 P 기어이거나 정차(0km/h) 중일 때만 열립니다. 주행 중 쪽지는 음성으로만 다룹니다.
- **사용한 Pleos SDK:** Vehicle 2.3.2, FusedLocation 1.1.3, TextToSpeech 3.3.1, SpeechToText 3.3.1, NaviHelper 3.0.5

## Pleos Connect 에뮬레이터 버전과 화면 규격

- 에뮬레이터: Pleos_Connect_v2.1.0 (`RELEASE.release_connect_v2.1.0`, Android 14 / API 34, x86_64)
- 화면: 2560 x 1440 가로, 160dpi
