# [현대차 App 공모전] 여행 도슨트(Travel Docent) DCX 데이터 수집 & 분석 구현 계획서

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현대차 App 공모전 출품작인 **인카 엔터테인먼트 '여행 도슨트(Travel Docent)'**의 서비스 기획 타당성 검증을 위해, 네이버 주요 자동차·전기차·차박·드라이브 동호회로부터 실제 운전자 및 동승자의 여행 중 PainPoint(DCX) 데이터를 `scrapling` 기반으로 수집·분류·정리한다.

**Architecture:** 
- 고속·우회 크롤링 엔진(`scrapling`의 `StealthyFetcher`/`Fetcher`)을 활용하여 네이버 카페/검색 데이터 수집 파이프라인 구축.
- 동호회별(현대모터클럽, 전기차동호회, 차박캠핑클럽, 전국드라이브동호회) 타겟 키워드 쿼리 실행.
- 수집된 원천 텍스트에서 4대 핵심 여정(경유지/스탬프/빌트인캠, 졸음방지/대화, 운전매너/마일리지, 블루멤버스 연계)별 페인포인트/니즈 자동 추출 및 정제.
- `data/네이버카페/[각 카페명]/` 디렉터리에 원천 데이터(`raw_posts.json`), 정제 데이터(`dcx_painpoints.json`), 동호회별 요약 리포트 저장.

**Tech Stack:** Python 3.14, `scrapling` (v0.5), `lxml`, `pandas`, `dataclasses`, `regex`

---

## 1. 서비스 기획 매핑 & 데이터 수집 전략

### 1.1 여행 도슨트(Travel Docent) 서비스 개요 및 데이터 매핑
| 여정 단계 | 서비스 기능 | 타겟 DCX PainPoint / 수집 키워드 |
| :--- | :--- | :--- |
| **Step 1: 목적지 설정** | 여행 테마 기반 목적지 설정 | 드라이브 코스 탐색 어려움, 뻔한 관광지 탈피 니즈 (`드라이브 코스`, `여행지 추천`) |
| **Step 2-1: 경유지 & 기록** | 휴게소/명소 정차 스탬프 + 빌트인캠 사진 자동 기록 | 휴게소 맛집/명소 정보 부족, 빌트인캠 사진 추출/정리 번거로움, 여행 기록 부재 (`휴게소 명소`, `빌트인캠 사진`, `여행 사진`) |
| **Step 2-2: 대화 모드** | 지루함·졸음 방지 대화/퀴즈/도슨트 설명 | 장거리 운전 피로, 동승자(아이들) 지루함, 단순 내비의 단조로움 (`장거리 운전 졸음`, `동승자 지루`, `오디오가이드`) |
| **Step 2-3: 운전 매너** | 급가감속/깜빡이 등 운전 매너 점수 기반 마일리지 | 안전운전에 대한 실질적 보상 부족, 단속 카메라 외 자발적 매너 동기부여 필요 (`운전점수`, `운전 매너`, `안전운전`) |
| **Step 3~4: 보상 & 연계** | 도착 마일리지 지급 & 블루멤버스 포인트샵 연동 | 모아둔 블루멤버스 포인트 실질 사용처 부족, 여행 경비 세이브 니즈 (`블루멤버스 포인트`, `포인트 사용`, `마일리지`) |

---

## 2. 수집 대상 카페 및 상세 수집 항목

### 타겟 1: 현대모터클럽 (hmcclub / 현대차 대표 공식 동호회)
- **성격**: 현대차 전 차종 오너 중심의 가장 큰 대표 커뮤니티 (아이오닉, 팰리세이드, 싼타페, 그랜저, 투싼 등)
- **수집 목적**: 현대차 오너들의 실제 장거리 주행 습관, 인포테인먼트(ccNC, 블루링크, 빌트인캠)에 대한 불만 및 기대, 가족 동승 여행 경험.
- **주요 키워드**: `장거리 여행`, `휴게소`, `빌트인캠 사진`, `블루링크`, `블루멤버스 포인트`, `동승자 지루`
- **저장 위치**: `data/네이버카페/현대모터클럽/`

### 타겟 2: 전기차동호회 (evclub / 전기차오너스 / 아이오닉 동호회)
- **성격**: 아이오닉5/6/9, EV6/9 등 국내 전기차 실사용자 중심 커뮤니티
- **수집 목적**: 전기차 특유의 장거리 여행 스트레스(충전 대기, 충전소 인근 즐길 거리 부재), 충전 시간 동안 차 안에서 겪는 지루함 및 V2L 엔터테인먼트 니즈.
- **주요 키워드**: `전기차 장거리`, `충전 대기`, `충전소 휴게소`, `충전 시간 지루`, `고속도로 충전`
- **저장 위치**: `data/네이버카페/전기차동호회/`

### 타겟 3: 차박캠핑클럽 (chcamping / 국내 최대 차박 커뮤니티)
- **성격**: 자동차를 단순 이동수단이 아닌 '체류 및 쉼'의 공간으로 활용하는 40~50만 회원 커뮤니티
- **수집 목적**: 경유지 탐색 니즈, 클린 캠핑 및 운전 매너 이슈, 차 안에서 머무는 시간 동안의 엔터테인먼트 및 로컬 연계 니즈.
- **주요 키워드**: `차박 여행`, `스텔스 차박`, `휴게소 차박`, `경유지`, `차량 내 엔터테인먼트`, `차박 매너`
- **저장 위치**: `data/네이버카페/차박캠핑클럽/`

### 타겟 4: 전국드라이브동호회 (drive / 로드트립 커뮤니티)
- **성격**: 주말 드라이브, 자동차 여행 경로, 명소 탐방 중심 커뮤니티
- **수집 목적**: 단순 빠른 길 찾기가 아닌 '경치 좋은 길', '경유할 만한 숨은 스팟', 이동 경로 상의 도슨트/해설 니즈 파악.
- **주요 키워드**: `드라이브 코스`, `경유 명소`, `국도 여행`, `주변 볼거리`, `도슨트`, `경치`
- **저장 위치**: `data/네이버카페/전국드라이브동호회/`

---

## 3. 디렉터리 및 파일 구조

```
Hyundai_app_data/
├── docs/
│   └── superpowers/
│       └── plans/
│           └── 2026-11-02-dcx-travel-docent-crawling.md
├── src/
│   ├── crawler/
│   │   ├── __init__.py
│   │   ├── config.py                 # 카페별 설정, 검색 쿼리, 셀렉터 정의
│   │   ├── scrapling_engine.py       # Scrapling StealthyFetcher/Fetcher 래퍼
│   │   └── cafe_crawler.py          # 네이버 카페 검색 및 게시글 수집기
│   ├── analyzer/
│   │   ├── __init__.py
│   │   └── painpoint_extractor.py   # DCX 페인포인트 분류 및 키워드 추출
│   └── pipeline.py                   # 전체 수집 및 분석 오케스트레이션 CLI
└── data/
    └── 네이버카페/
        ├── 현대모터클럽/
        │   ├── raw_posts.json
        │   ├── dcx_painpoints.json
        │   └── summary.md
        ├── 전기차동호회/
        │   ├── raw_posts.json
        │   ├── dcx_painpoints.json
        │   └── summary.md
        ├── 차박캠핑클럽/
        │   ├── raw_posts.json
        │   ├── dcx_painpoints.json
        │   └── summary.md
        ├── 전국드라이브동호회/
        │   ├── raw_posts.json
        │   ├── dcx_painpoints.json
        │   └── summary.md
        └── integrated_dcx_insights.json  # 공모전 기획서 작성용 통합 통계
```

---

## 4. 구현 태스크 상세 (Tasks Breakdown)

### Task 1: 크롤러 환경 및 타겟 설정 모듈 작성
**Files:**
- Create: `src/crawler/config.py`
- Test: `tests/test_config.py`

- [ ] 4개 타겟 카페(현대모터클럽, 전기차동호회, 차박캠핑클럽, 전국드라이브동호회) 정보와 검색 키워드 정의
- [ ] 여행 도슨트 4대 기능별 매핑 카테고리 정의
- [ ] 출력 디렉터리 자동 생성 로직 포함
- [ ] 단위 테스트 작성 및 검증

### Task 2: Scrapling 기반 네이버 카페 크롤러 엔진 구현
**Files:**
- Create: `src/crawler/scrapling_engine.py`
- Create: `src/crawler/cafe_crawler.py`
- Test: `tests/test_crawler.py`

- [ ] `scrapling.StealthyFetcher` 및 `scrapling.Fetcher`를 활용한 봇 탐지 우회 fetcher 래퍼 구현
- [ ] 네이버 검색의 카페 탭 검색 결과 파싱 (게시글 제목, 링크, 작성일, 카페명, 본문 스니펫 추출)
- [ ] Rate limiting (네이버 부하 방지 및 차단 예방 1~2초 랜덤 딜레이) 구현
- [ ] 각 카페별 타겟 게시글 추출 테스트 및 검증

### Task 3: DCX 페인포인트 추출 및 분류기 구현
**Files:**
- Create: `src/analyzer/painpoint_extractor.py`
- Test: `tests/test_analyzer.py`

- [ ] 게시글 텍스트에서 불만/불편(PainPoint) 및 개선 요구(Need) 키워드 매칭 로직
- [ ] 여행 도슨트의 4가지 기능(경유지/스탬프/빌트인캠, 졸음방지 대화, 운전매너 마일리지, 블루멤버스 연계)별 분류 스코어링
- [ ] 감성/니즈 강도 태깅 (예: [높음] "휴게소 맛집 찾기 너무 번거로움", [중간] "빌트인캠 사진 폰으로 옮기기 귀찮음")
- [ ] 분류 결과 JSON 스키마 포맷팅

### Task 4: 전체 수집 및 data/ 폴더 자동 저장 파이프라인 가동
**Files:**
- Create: `src/pipeline.py`

- [ ] 타겟 4개 카페 전체에 대해 스크래핑 파이프라인 일괄 실행
- [ ] `data/네이버카페/[카페명]/raw_posts.json` 저장
- [ ] `data/네이버카페/[카페명]/dcx_painpoints.json` 저장
- [ ] `data/네이버카페/[카페명]/summary.md` 생성 (주요 불만 사항 TOP 5, 사용자 인용문 포함)

### Task 5: 공모전 출품용 통합 DCX 인사이트 리포트 생성
**Files:**
- Create: `data/네이버카페/integrated_dcx_insights.json`
- Create: `data/네이버카페/integrated_dcx_report.md`

- [ ] 4개 카페의 데이터를 종합하여 "여행 도슨트 앱 기획을 뒷받침하는 핵심 근거" 정리
- [ ] 기획서의 각 기능(경유지 기록, 대화 모드, 운전 매너 마일리지, 블루멤버스)에 대응되는 실제 고객 보이스(VOC) 및 통계 인포그래픽 요약

