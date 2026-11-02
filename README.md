# 🚗 현대자동차 앱 공모전 - 차세대 SDV OS 'PLEOS' 탑재 인카 앱 "여행 도슨트(Travel Docent)" DCX 원천 데이터셋

현대자동차그룹의 차세대 소프트웨어 중심 차량(SDV) OS인 **PLEOS(플레오스)**에 탑재될 인카 엔터테인먼트 및 안전운전 케어 서비스 **"여행 도슨트 (Travel Docent)"** 기획 및 디지털 고객 경험(DCX) 정밀 분석을 위한 **100% 실사용자 기반 고순도 원천 데이터셋(Authentic Raw Datasets)** 저장소입니다.

---

## 📌 1. 프로젝트 및 서비스 개요

* **출품명**: 여행 도슨트 (Travel Docent on PLEOS)
* **목표 플랫폼**: 현대자동차그룹 차세대 SDV 운영체제 **PLEOS**
* **핵심 서비스 시나리오**:
  1. **경유지 스탬프 & 자동 풍경 사진 기록 (Feature 2-1)**: 빌트인 캠 2 및 차량 외장 카메라(SVM) API와 연동하여 휴게소/명소 정차 시 운전자 조작 없이 차량 시점 랜드마크 자동 촬영 및 스탬프 앨범화
  2. **차내 대화형 AI 도슨트 & 졸음 예방 퀴즈 (Feature 2-2)**: 차내 마이크/스피커 및 IVI 음성 AI를 통해 고속도로 운전 중 자연스러운 대화, 지역 스토리텔링, 상식 퀴즈로 졸음운전 및 동승자 지루함 방지
  3. **차량 CAN 텔레매틱스 기반 운전 매너 점수 (Feature 2-3)**: 스마트폰 GPS 기반 급가감속 오판정을 극복하고, 차량 CAN 신호(방향지시등 점등률, 부드러운 가감속, 우회전 보행자 일시정지)를 정밀 측정하여 신뢰성 있는 매너 점수 산출
  4. **블루멤버스 및 차량 내 마일리지 보상 연계 (Feature 3~4)**: 안전운전 및 경유지 인증으로 획득한 마일리지를 현대차 블루멤버스 포인트로 즉시 전환하거나 인카 포인트샵에서 휴게소 커피·전기차 충전·정비 쿠폰으로 즉시 사용

---

## 📊 2. 수집된 원천 데이터 총괄 현황 (총 20,542건)

> **💡 데이터 무결성 원칙 (Data Integrity & Authenticity)**  
> * 모의/가짜/합성 데이터(Mock / Synthetic / LLM 생성) **0.0% (Zero Fake Data)**
> * 최근 24개월(2024~2026) 이내 작성된 최신성 데이터
> * 전화번호, 이메일, 차량번호 등 개인정보(PII) 정밀 마스킹 완료
> * 원문 텍스트 요약이나 왜곡 없이 날 것 그대로 JSON / CSV (UTF-8-SIG) / JSONL 보존

| 데이터 분류 | 세부 출처 / 서비스 | 수집 수량 | 주요 수집 내용 및 핵심 VOC |
| :--- | :--- | :---: | :--- |
| **국내 벤치마크 실앱 리뷰** | **티맵 (TMAP)** | 9,804건 | 운전점수 급감 감점 불만, 급제동 오인정, 보험료 할인 연계 스트레스 |
| | **카카오내비** | 1,973건 | 안전운전 점수, 주행 동선 안내, 내비게이션 정확도 |
| | **위드라이브 (WeDrive)** | 287건 | 주행거리 M2E 마일리지 보상, 배터리 소모, 광고 피로 |
| | **마이현대 (MyHyundai)** | 141건 | 블루멤버스 포인트 조회/사용, 차량 정비 및 디지털 키 연동 |
| | **스탬프투어 & 오디 & Autio** | 91건 | GPS 위치기반 오디오 도슨트, 지자체 관광 스탬프 인증 불편 |
| | *국내 앱스토어 소계* | *12,296건* | |
| **해외 글로벌 로드트립 리뷰** | **Wanderlog (원더로그)** | 4,399건 | 장거리 로드트립 동선 플래닝, 오프라인 맵, 여행 기록 불편 |
| | **PlugShare (플러그셰어)** | 1,642건 | 전기차 고속도로 충전기 고장, 대기 줄, 충전 중 편의시설 부재 |
| | **ABRP (일렉트릭 루트플래너)** | 909건 | 고속도로 충전소 헛걸음, 배터리 소모율 오차, 차내 UI 렉 |
| | **Roadtrippers (로드트리퍼스)** | 621건 | 경유지 탐색 번거로움, 강제 과금, 경로 왜곡 (불만율 80.5%) |
| | *해외 앱스토어 소계* | *7,571건* | |
| **국내 커뮤니티 실차주 VOC** | **보배드림 & 뽐뿌 자동차포럼** | 510건 | 본문 원문 + 상위 댓글 전체 (장거리 주행 피로, 졸음운전, EV 충전) |
| | **아카라이브 (자동차/캠핑 채널)** | 130건 | 2026년 실차주 날 것의 목소리 (졸음쉼터 부족, 운전점수 조작 시도 등) |
| | *커뮤니티 소계* | *640건* | |
| **고속도로 휴게소 공공데이터** | **한국도로공사 공공데이터** | 30건 | EX-FOOD 맛집 20선, 편의시설 및 초급속 E-pit 충전소 현황 |
| **국제 학술 연구 논문** | **ACM / IEEE ITS / ACM CHI** | 5편 | 인카 음성 대화의 졸음예방 효과 및 운전점수 게이미피케이션 학술 근거 |
| **총계** | **100% 검증 가능한 순수 원천 데이터** | **20,542건** | **국내외 실차주 및 여행자들의 방대한 페인포인트 아카이브** |

---

## 📁 3. 디렉터리 구조

```text
Hyundai_app_data/
├── README.md                                  # 프로젝트 및 데이터셋 상세 안내서
├── .gitignore                                 # Git 추적 제외 설정
├── data/
│   ├── 벤치마크_유사서비스/
│   │   └── 원천리뷰데이터/
│   │       ├── 티맵_TMAP_raw_reviews.json / .csv
│   │       ├── 카카오내비_KakaoNavi_raw_reviews.json / .csv
│   │       ├── 위드라이브_WeDrive_raw_reviews.json / .csv
│   │       ├── 마이현대_MyHyundai_raw_reviews.json / .csv
│   │       ├── 오디_Odii_raw_reviews.json / .csv
│   │       ├── 스탬프투어_StampTour_raw_reviews.json / .csv
│   │       ├── all_benchmark_reviews_raw.jsonl (국내 통합)
│   │       └── 해외_글로벌_로드트립_EV/
│   │           ├── Roadtrippers_로드트리퍼스_raw_reviews.json / .csv
│   │           ├── Wanderlog_원더로그_raw_reviews.json / .csv
│   │           ├── ABRP_일렉트릭_루트플래너_raw_reviews.json / .csv
│   │           ├── PlugShare_플러그셰어_raw_reviews.json / .csv
│   │           └── all_global_roadtrip_reviews_raw.jsonl (해외 통합)
│   ├── 공공데이터_휴게소/
│   │   ├── 전국_고속도로_휴게소_대표맛집_EX_FOOD.json / .csv
│   │   └── 전국_고속도로_휴게소_편의시설_현황.json / .csv
│   └── 논문/
│       ├── academic_research_papers.json
│       └── literature_review_summary.md
├── dcx_collector/                             # 커뮤니티 원천 VOC 수집 모듈
│   ├── GEMINI.md                              # 데이터 수집 절대 준수 지침서
│   ├── collect.py                             # 보배드림 / 뽐뿌 수집기
│   ├── arca_collect.py                        # 아카라이브 자동차/캠핑 채널 수집기
│   ├── reddit_collect.py                      # Reddit 공식 PRAW 수집 모듈
│   └── data/
│       ├── 수집결과.csv                        # 커뮤니티 640건 통합 마스터 CSV
│       └── [유형별]/[출처별]/posts.jsonl
└── src/                                       # 앱스토어 수집 파이프라인 스크립트
    ├── collect_benchmark_reviews.py           # 국내 벤치마크 앱 수집기
    └── collect_global_roadtrip_reviews.py     # 해외 글로벌 로드트립 앱 수집기
```

---

## 🛠️ 4. 데이터 수집 실행 방법

```bash
# 가상환경 또는 로컬 파이썬 환경 (Python 3.10+)
pip install google-play-scraper requests beautifulsoup4 scrapling

# 1. 국내 벤치마크 실앱 리뷰 전수 수집
python src/collect_benchmark_reviews.py

# 2. 해외 글로벌 로드트립 & EV 리뷰 전수 수집
python src/collect_global_roadtrip_reviews.py

# 3. 아카라이브 최신 자동차/캠핑 커뮤니티 VOC 수집
python dcx_collector/arca_collect.py --max-posts 100
```
