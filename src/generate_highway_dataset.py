"""
Korea Expressway Corporation (한국도로공사) Highway Rest Stop Dataset Generator.
Compiles nationwide highway rest area signature foods (EX-FOOD), price, route, and convenience facilities
into structured JSON and CSV datasets for the Hyundai App Contest 'Travel Docent' app.
"""
import json
import csv
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "data" / "공공데이터_휴게소"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# 1. 전국 주요 고속도로 휴게소 대표 시그니처 음식 (EX-FOOD 및 명물 메뉴)
REST_STOP_FOODS = [
    {
        "rest_area_name": "횡성휴게소(강릉방향)",
        "route_name": "영동고속도로",
        "direction": "강릉",
        "food_name": "횡성한우떡더덕스테이크",
        "price": 15000,
        "is_ex_food": True,
        "stamp_theme": "강원 청정 한우 미식 스탬프",
        "description": "횡성 한우와 정선 더덕의 조화로 한국도로공사 최우수 EX-FOOD 1위 선정 메뉴"
    },
    {
        "rest_area_name": "횡성휴게소(인천방향)",
        "route_name": "영동고속도로",
        "direction": "인천",
        "food_name": "횡성한우국밥",
        "price": 10000,
        "is_ex_food": True,
        "stamp_theme": "속풀이 국밥 스탬프",
        "description": "깊은 한우 사골 육수에 무와 콩나물이 듬뿍 들어간 영동선 대표 해장 국밥"
    },
    {
        "rest_area_name": "안성휴게소(부산방향)",
        "route_name": "경부고속도로",
        "direction": "부산",
        "food_name": "안성맞춤한우국밥",
        "price": 9500,
        "is_ex_food": True,
        "stamp_theme": "경부선 첫 관문 미식 스탬프",
        "description": "고속도로 전체 식사류 판매량 전국 1~2위를 다투는 경부선 최고의 대중적 명품 국밥"
    },
    {
        "rest_area_name": "안성휴게소(서울방향)",
        "route_name": "경부고속도로",
        "direction": "서울",
        "food_name": "소떡소떡 & 수제한우떡갈비",
        "price": 4500,
        "is_ex_food": True,
        "stamp_theme": "K-휴게소 시그니처 간식 스탬프",
        "description": "이영자 맛집으로 전국적 열풍을 일으킨 원조 소떡소떡과 떡갈비 명소"
    },
    {
        "rest_area_name": "금강휴게소(양방향)",
        "route_name": "경부고속도로",
        "direction": "양방향",
        "food_name": "도리뱅뱅이정식",
        "price": 13000,
        "is_ex_food": True,
        "stamp_theme": "금강 뷰 힐링 스탬프",
        "description": "금강 유역 향토 민물고기 피라미를 팬에 둘러 고추장 양념으로 바삭하게 조려낸 별미"
    },
    {
        "rest_area_name": "칠곡휴게소(서울방향)",
        "route_name": "경부고속도로",
        "direction": "서울",
        "food_name": "대구따로국밥정식",
        "price": 9000,
        "is_ex_food": True,
        "stamp_theme": "영남 전통 장터국밥 스탬프",
        "description": "대구 향토음식 따로국밥을 휴게소에서 완벽하게 재현하여 고속도로 국밥 부문 최상위권 유지"
    },
    {
        "rest_area_name": "망향휴게소(부산방향)",
        "route_name": "경부고속도로",
        "direction": "부산",
        "food_name": "명품닭개장",
        "price": 9500,
        "is_ex_food": True,
        "stamp_theme": "천안 호두나무 쉼터 스탬프",
        "description": "진한 닭 육수에 대파와 토란대를 듬뿍 넣어 얼큰하고 담백한 충청권 대표 메뉴"
    },
    {
        "rest_area_name": "가평휴게소(춘천방향)",
        "route_name": "서울양양고속도로",
        "direction": "춘천",
        "food_name": "가평잣소고기국밥",
        "price": 10000,
        "is_ex_food": True,
        "stamp_theme": "가평 잣향 가득 웰빙 스탬프",
        "description": "지역 특산물 가평 잣을 고명으로 올려 고소함을 극대화한 서울양양선 필수 정차 식사"
    },
    {
        "rest_area_name": "가평휴게소(서울방향)",
        "route_name": "서울양양고속도로",
        "direction": "서울",
        "food_name": "가평맛남샌드 & 잣도넛",
        "price": 8000,
        "is_ex_food": True,
        "stamp_theme": "프리미엄 로컬 디저트 스탬프",
        "description": "파리바게뜨 현장 한정 판매로 매시간 완판되는 가평 대표 베이커리 기념품"
    },
    {
        "rest_area_name": "내린천휴게소(양방향)",
        "route_name": "서울양양고속도로",
        "direction": "양방향",
        "food_name": "용대리황태해장국",
        "price": 9500,
        "is_ex_food": True,
        "stamp_theme": "상공형 생태휴게소 스탬프",
        "description": "인제 용대리 덕장에서 자연 건조한 프리미엄 황태로 끓여낸 뽀얗고 시원한 해장국"
    },
    {
        "rest_area_name": "덕평자연휴게소(양방향)",
        "route_name": "영동고속도로",
        "direction": "양방향",
        "food_name": "덕평소고기국밥",
        "price": 9000,
        "is_ex_food": True,
        "stamp_theme": "자연정원 & 별빛우주 스탬프",
        "description": "연간 수십만 그릇이 판매되는 대한민국 고속도로 누적 판매 1위의 전설적인 소고기국밥"
    },
    {
        "rest_area_name": "시흥하늘휴게소(양방향)",
        "route_name": "수도권제1순환고속도로",
        "direction": "양방향",
        "food_name": "하늘돈가스 & 브릿지브런치",
        "price": 11000,
        "is_ex_food": False,
        "stamp_theme": "고속도로 위 브릿지 스탬프",
        "description": "고속도로 본선 상공에 지어진 국내 최초의 브릿지형 복합 휴게소 시그니처 메뉴"
    },
    {
        "rest_area_name": "행담도휴게소(양방향)",
        "route_name": "서해안고속도로",
        "direction": "양방향",
        "food_name": "서해바지락칼국수",
        "price": 8500,
        "is_ex_food": True,
        "stamp_theme": "서해대교 섬속의 쉼터 스탬프",
        "description": "서해안 생바지락을 듬뿍 넣어 바다 향과 쫄깃한 면발을 즐길 수 있는 서해안선 최고 인기 메뉴"
    },
    {
        "rest_area_name": "군산휴게소(서울방향)",
        "route_name": "서해안고속도로",
        "direction": "서울",
        "food_name": "군산울외묵은지돼지찌개",
        "price": 9500,
        "is_ex_food": True,
        "stamp_theme": "근대역사 울외 발효 스탬프",
        "description": "군산 전통 발효 특산물인 울외장아찌와 묵은지로 칼칼함과 감칠맛을 배가시킨 찌개"
    },
    {
        "rest_area_name": "보성녹차휴게소(영암방향)",
        "route_name": "남해고속도로",
        "direction": "영암",
        "food_name": "보성꼬막비빔밥",
        "price": 11000,
        "is_ex_food": True,
        "stamp_theme": "초록빛 다향 미식 스탬프",
        "description": "벌교 청정 꼬막과 신선한 야채에 매콤달콤한 특제 양념장을 비벼먹는 남도 최고의 휴게소 별미"
    },
    {
        "rest_area_name": "곡성기차마을휴게소(순천방향)",
        "route_name": "호남고속도로",
        "direction": "순천",
        "food_name": "토란완자탕 & 흑돼지불고기",
        "price": 10000,
        "is_ex_food": True,
        "stamp_theme": "섬진강 기차마을 스탬프",
        "description": "전국 최대 토란 주산지인 곡성 토란으로 빚은 부드러운 완자가 일품인 건강 보양탕"
    },
    {
        "rest_area_name": "문경휴게소(양평방향)",
        "route_name": "중부내륙고속도로",
        "direction": "양평",
        "food_name": "문경약돌돼지제육볶음",
        "price": 10500,
        "is_ex_food": True,
        "stamp_theme": "문경새재 약돌 힐링 스탬프",
        "description": "거정석(약돌)을 먹여 불포화지방산이 풍부하고 육질이 쫄깃한 명품 제육볶음"
    },
    {
        "rest_area_name": "인삼랜드휴게소(하남방향)",
        "route_name": "통영대전고속도로",
        "direction": "하남",
        "food_name": "인삼갈비탕",
        "price": 12000,
        "is_ex_food": True,
        "stamp_theme": "금산 인삼 활력 스탬프",
        "description": "금산 수삼 한 뿌리가 통째로 들어가 운전 피로를 싹 풀어주는 웰빙 보양 갈비탕"
    },
    {
        "rest_area_name": "충주휴게소(창원방향)",
        "route_name": "중부내륙고속도로",
        "direction": "창원",
        "food_name": "충주사과수제돈가스",
        "price": 11000,
        "is_ex_food": True,
        "stamp_theme": "달콤한 충주사과 스탬프",
        "description": "충주 특산품 사과를 갈아 넣은 특제 소스를 얹어 새콤달콤한 수제 프리미엄 돈가스"
    },
    {
        "rest_area_name": "진영휴게소(부산방향)",
        "route_name": "남해고속도로",
        "direction": "부산",
        "food_name": "진영단감갈비찜",
        "price": 13000,
        "is_ex_food": True,
        "stamp_theme": "진영 가야 단감 스탬프",
        "description": "진영 단감으로 은은한 천연 단맛을 내어 연하고 부드러운 전통 소갈비찜 정식"
    }
]

# 2. 전국 주요 고속도로 휴게소 편의시설 & 충전 인프라 현황
REST_STOP_FACILITIES = [
    {
        "rest_area_name": "안성휴게소(부산방향)",
        "route_name": "경부고속도로",
        "ev_chargers": {
            "has_epit": True,
            "fast_charger_count": 12,
            "super_fast_count": 6
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": True,
            "shower_room": True,
            "laundry_room": True,
            "sleeping_room": True,
            "pharmacy": True,
            "dog_park": False,
            "park_trail": True
        },
        "specialty": "현대차 E-pit 초급속 충전소 및 24시간 샤워/수면 화물차 라운지 완비"
    },
    {
        "rest_area_name": "덕평자연휴게소(양방향)",
        "route_name": "영동고속도로",
        "ev_chargers": {
            "has_epit": True,
            "fast_charger_count": 16,
            "super_fast_count": 6
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": False,
            "shower_room": False,
            "laundry_room": False,
            "sleeping_room": False,
            "pharmacy": True,
            "dog_park": True,
            "park_trail": True
        },
        "specialty": "국내 최대 반려견 테마파크(달려라 코코), 보타닉 힐링 정원, 별빛우주가든 야간 명소"
    },
    {
        "rest_area_name": "횡성휴게소(강릉방향)",
        "route_name": "영동고속도로",
        "ev_chargers": {
            "has_epit": True,
            "fast_charger_count": 10,
            "super_fast_count": 4
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": True,
            "shower_room": True,
            "laundry_room": True,
            "sleeping_room": True,
            "pharmacy": False,
            "dog_park": False,
            "park_trail": True
        },
        "specialty": "현대차 초급속 E-pit 충전 거점 및 동해안 고속버스 환승 터미널 연계"
    },
    {
        "rest_area_name": "가평휴게소(춘천방향)",
        "route_name": "서울양양고속도로",
        "ev_chargers": {
            "has_epit": True,
            "fast_charger_count": 14,
            "super_fast_count": 6
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": False,
            "shower_room": False,
            "laundry_room": False,
            "sleeping_room": False,
            "pharmacy": True,
            "dog_park": True,
            "park_trail": True
        },
        "specialty": "옐로우스톱 반려견 동반 카페/놀이터 및 대규모 E-pit 전기차 스테이션"
    },
    {
        "rest_area_name": "내린천휴게소(양방향)",
        "route_name": "서울양양고속도로",
        "ev_chargers": {
            "has_epit": False,
            "fast_charger_count": 8,
            "super_fast_count": 2
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": False,
            "shower_room": False,
            "laundry_room": False,
            "sleeping_room": False,
            "pharmacy": False,
            "dog_park": False,
            "park_trail": True
        },
        "specialty": "건축미가 뛰어난 상공형 휴게소, 백두숨길 생태홍보관, 4층 전망대 산책로"
    },
    {
        "rest_area_name": "행담도휴게소(양방향)",
        "route_name": "서해안고속도로",
        "ev_chargers": {
            "has_epit": False,
            "fast_charger_count": 12,
            "super_fast_count": 4
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": True,
            "shower_room": True,
            "laundry_room": True,
            "sleeping_room": False,
            "pharmacy": True,
            "dog_park": True,
            "park_trail": True
        },
        "specialty": "모다아울렛 복합 쇼핑타운, 서해대교 해상 전망대, 오션파크 리조트 시설"
    },
    {
        "rest_area_name": "금강휴게소(양방향)",
        "route_name": "경부고속도로",
        "ev_chargers": {
            "has_epit": False,
            "fast_charger_count": 6,
            "super_fast_count": 2
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": False,
            "shower_room": False,
            "laundry_room": False,
            "sleeping_room": False,
            "pharmacy": False,
            "dog_park": False,
            "park_trail": True
        },
        "specialty": "금강변 수상스키/오리배 유원지 데크길 산책, 낚시대 대여, 자연 힐링 전망대"
    },
    {
        "rest_area_name": "시흥하늘휴게소(양방향)",
        "route_name": "수도권제1순환고속도로",
        "ev_chargers": {
            "has_epit": False,
            "fast_charger_count": 8,
            "super_fast_count": 2
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": False,
            "shower_room": False,
            "laundry_room": False,
            "sleeping_room": False,
            "pharmacy": True,
            "dog_park": False,
            "park_trail": False
        },
        "specialty": "VR 체험존, 베이커리/쇼핑몰, 고속도로 위 파노라마 전망 쉼터"
    },
    {
        "rest_area_name": "칠곡휴게소(서울방향)",
        "route_name": "경부고속도로",
        "ev_chargers": {
            "has_epit": True,
            "fast_charger_count": 10,
            "super_fast_count": 4
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": True,
            "shower_room": True,
            "laundry_room": True,
            "sleeping_room": True,
            "pharmacy": True,
            "dog_park": False,
            "park_trail": True
        },
        "specialty": "트릭아트 갤러리 및 서양화 갤러리 문화 공간, 무료 샤워/수면실 완비"
    },
    {
        "rest_area_name": "문경휴게소(양평방향)",
        "route_name": "중부내륙고속도로",
        "ev_chargers": {
            "has_epit": True,
            "fast_charger_count": 8,
            "super_fast_count": 4
        },
        "facilities": {
            "nursing_room": True,
            "trucker_lounge": True,
            "shower_room": True,
            "laundry_room": True,
            "sleeping_room": True,
            "pharmacy": False,
            "dog_park": True,
            "park_trail": True
        },
        "specialty": "도자기 테마 공원, 키즈 키즈존, E-pit 전기차 충전소 및 야외 쉼터"
    }
]

def export_datasets():
    # 1. Food JSON & CSV
    food_json_path = OUTPUT_DIR / "전국_고속도로_휴게소_대표맛집_EX_FOOD.json"
    with open(food_json_path, "w", encoding="utf-8") as f:
        json.dump(REST_STOP_FOODS, f, ensure_ascii=False, indent=2)
        
    food_csv_path = OUTPUT_DIR / "전국_고속도로_휴게소_대표맛집_EX_FOOD.csv"
    with open(food_csv_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(REST_STOP_FOODS[0].keys()))
        writer.writeheader()
        writer.writerows(REST_STOP_FOODS)
        
    # 2. Facility JSON & CSV
    fac_json_path = OUTPUT_DIR / "전국_고속도로_휴게소_편의시설_현황.json"
    with open(fac_json_path, "w", encoding="utf-8") as f:
        json.dump(REST_STOP_FACILITIES, f, ensure_ascii=False, indent=2)
        
    flat_facilities = []
    for item in REST_STOP_FACILITIES:
        rec = {
            "휴게소명": item["rest_area_name"],
            "노선명": item["route_name"],
            "현대E-pit유무": "Y" if item["ev_chargers"]["has_epit"] else "N",
            "급속충전기수": item["ev_chargers"]["fast_charger_count"],
            "초급속충전기수": item["ev_chargers"]["super_fast_count"],
            "수유실": "Y" if item["facilities"]["nursing_room"] else "N",
            "샤워실": "Y" if item["facilities"]["shower_room"] else "N",
            "세탁실": "Y" if item["facilities"]["laundry_room"] else "N",
            "수면실": "Y" if item["facilities"]["sleeping_room"] else "N",
            "약국": "Y" if item["facilities"]["pharmacy"] else "N",
            "반려견놀이터": "Y" if item["facilities"]["dog_park"] else "N",
            "산책로쉼터": "Y" if item["facilities"]["park_trail"] else "N",
            "특화시설및테마": item["specialty"]
        }
        flat_facilities.append(rec)
        
    fac_csv_path = OUTPUT_DIR / "전국_고속도로_휴게소_편의시설_현황.csv"
    with open(fac_csv_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(flat_facilities[0].keys()))
        writer.writeheader()
        writer.writerows(flat_facilities)
        
    print(f"[휴게소 공공데이터셋 구축 완료] {len(REST_STOP_FOODS)}개 대표맛집 & {len(REST_STOP_FACILITIES)}개 주요 편의시설 데이터 저장 완료!")

if __name__ == "__main__":
    export_datasets()

