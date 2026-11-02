"""
DCX Data Collection Configuration for Hyundai App Contest (Travel Docent).
Defines target communities, search keywords mapped to Travel Docent features, and data output paths.
"""
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Dict

BASE_DIR = Path(__file__).resolve().parent.parent.parent
DATA_DIR = BASE_DIR / "data"

@dataclass
class SearchTopic:
    category: str
    feature_mapping: str
    keywords: List[str]

TOPICS: List[SearchTopic] = [
    SearchTopic(
        category="휴게소_경유_기록",
        feature_mapping="Feature 2-1: 경유지 스탬프 & 빌트인캠 사진 기록",
        keywords=["휴게소", "휴게소 맛집", "휴게소 명소", "경유지", "빌트인캠 사진", "여행 기록"]
    ),
    SearchTopic(
        category="장거리_졸음_지루함",
        feature_mapping="Feature 2-2: 졸음방지 대화 모드 & 인카 도슨트",
        keywords=["장거리 운전", "졸음운전", "지루", "동승자", "아이들 차안", "드라이브 코스"]
    ),
    SearchTopic(
        category="전기차_충전_여행",
        feature_mapping="Feature 2-1 & 2-2: 충전 대기 경유지 & 대화/엔터테인먼트",
        keywords=["전기차 장거리", "전기차 휴게소", "충전 대기", "충전 시간 지루", "고속도로 충전"]
    ),
    SearchTopic(
        category="차박_체류_여가",
        feature_mapping="Feature 2-1: 차박/경유지 스탬프 및 로컬 연계",
        keywords=["차박 여행", "스텔스 차박", "휴게소 차박", "차박 매너", "차내 엔터테인먼트"]
    ),
    SearchTopic(
        category="운전매너_포인트보상",
        feature_mapping="Feature 2-3 & 4: 운전매너 마일리지 & 블루멤버스 연동",
        keywords=["운전점수", "안전운전", "급가속", "블루멤버스", "포인트 사용", "마일리지"]
    ),
]

COMMUNITIES = {
    "clien_cm_car": {
        "name": "클리앙_굴러간당",
        "type": "clien",
        "board_cd": "cm_car",
        "output_dir": DATA_DIR / "클리앙_굴러간당"
    },
    "ppomppu_car": {
        "name": "뽐뿌_자동차포럼",
        "type": "ppomppu",
        "board_id": "car",
        "output_dir": DATA_DIR / "뽐뿌_자동차포럼"
    },
    "naver_motorsjason": {
        "name": "남차카페 (motorsjason)",
        "type": "naver_cafe",
        "cafe_url": "motorsjason",
        "output_dir": DATA_DIR / "네이버카페" / "남차카페"
    },
    "naver_hmcclub": {
        "name": "현대모터클럽 (hmcclub)",
        "type": "naver_cafe",
        "cafe_url": "hmcclub",
        "output_dir": DATA_DIR / "네이버카페" / "현대모터클럽"
    }
}

