"""
Raw Review Collector for Benchmark Services (Google Play Store).
Collects raw unsummarized user reviews for:
1. 위드라이브 (kr.wedrive.cash) - Driving mileage / M2E
2. 티맵 (com.skt.tmap.ku) - Driving score & navigation
3. 오디 Odii (kto.smarttour) - Korea Tourism Organization Audio Docent
4. 마이현대 (com.hyundai.myhyundai) - Hyundai BlueMembers & vehicle management
5. 스탬프투어 (com.thatzit.stamptour3) - Location stamp tour
6. 카카오내비 (com.locnall.KimGiSa) - Driving habits & navigation
7. Autio (autio.audio.travel.guide.stories) - Global in-car road trip audio guide

Saves raw reviews to data/벤치마크_유사서비스/원천리뷰데이터/ in JSON, JSONL, and CSV formats.
"""
import os
import sys
import json
import csv
import re
from pathlib import Path
from datetime import datetime
from google_play_scraper import reviews, Sort

sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "data" / "벤치마크_유사서비스" / "원천리뷰데이터"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

TARGET_APPS = [
    {
        "name": "위드라이브_WeDrive",
        "app_id": "kr.wedrive.cash",
        "lang": "ko",
        "country": "kr",
        "description": "운전 리워드 및 주행 마일리지 M2E 서비스"
    },
    {
        "name": "티맵_TMAP",
        "app_id": "com.skt.tmap.ku",
        "lang": "ko",
        "country": "kr",
        "description": "운전점수, 급가감속 판정, 내비게이션"
    },
    {
        "name": "오디_Odii",
        "app_id": "kto.smarttour",
        "lang": "ko",
        "country": "kr",
        "description": "한국관광공사 공식 위치기반 관광 오디오 도슨트"
    },
    {
        "name": "마이현대_MyHyundai",
        "app_id": "com.hyundai.myhyundai",
        "lang": "ko",
        "country": "kr",
        "description": "현대자동차 공식 블루멤버스, 포인트, 차량 관리"
    },
    {
        "name": "스탬프투어_StampTour",
        "app_id": "com.thatzit.stamptour3",
        "lang": "ko",
        "country": "kr",
        "description": "전국 지자체 및 명소 방문 스탬프 투어"
    },
    {
        "name": "카카오내비_KakaoNavi",
        "app_id": "com.locnall.KimGiSa",
        "lang": "ko",
        "country": "kr",
        "description": "운전기록, 안전운전 점수, 주행 안내"
    },
    {
        "name": "Autio_오티오",
        "app_id": "autio.audio.travel.guide.stories",
        "lang": "en",
        "country": "us",
        "description": "글로벌 1위 차량용 로드트립 GPS 오디오 스토리텔링"
    }
]

PII_PATTERNS = [
    (re.compile(r"01\d[-.\s]?\d{3,4}[-.\s]?\d{4}"), "[전화]"),
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"), "[이메일]"),
]

def mask_pii(text: str) -> str:
    if not text:
        return ""
    for rx, rep in PII_PATTERNS:
        text = rx.sub(rep, text)
    return text

def collect_app_reviews(target: dict, target_count: int = 15000) -> list:
    name = target["name"]
    app_id = target["app_id"]
    lang = target["lang"]
    country = target["country"]
    
    print(f"\n[수집 시작] {name} ({app_id}) - 최대 목표: {target_count}건...")
    
    all_revs = []
    seen_ids = set()
    cutoff_year = datetime.now().year - 2  # 최근 24개월 (2024년 이후)
    
    # 1. 최신순(NEWEST) 페이지네이션 대량 수집
    try:
        current_token = None
        while len(all_revs) < target_count:
            fetch_count = min(target_count - len(all_revs), 1000)
            if current_token is None:
                batch, current_token = reviews(
                    app_id,
                    lang=lang,
                    country=country,
                    sort=Sort.NEWEST,
                    count=fetch_count
                )
            else:
                batch, current_token = reviews(app_id, continuation_token=current_token)
                
            if not batch:
                break
                
            added = 0
            reached_cutoff = False
            for r in batch:
                rid = r.get("reviewId")
                if rid and rid not in seen_ids:
                    seen_ids.add(rid)
                    at_val = r.get("at")
                    if at_val and at_val.year < cutoff_year:
                        reached_cutoff = True
                        break
                    raw_content = r.get("content", "")
                    if not raw_content:
                        continue
                    
                    date_str = at_val.strftime("%Y-%m-%d %H:%M:%S") if isinstance(at_val, datetime) else str(at_val)
                    
                    cleaned_rec = {
                        "review_id": rid,
                        "app_name": name,
                        "app_id": app_id,
                        "score": r.get("score"),
                        "thumbs_up_count": r.get("thumbsUpCount", 0),
                        "date": date_str,
                        "review_text": mask_pii(raw_content),
                        "developer_reply": mask_pii(r.get("replyContent") or ""),
                        "app_version": r.get("reviewCreatedVersion") or ""
                    }
                    all_revs.append(cleaned_rec)
                    added += 1
            print(f"  - [NEWEST] +{added}건 (누적 {len(all_revs)}건)")
            if reached_cutoff or current_token is None or added == 0:
                break
    except Exception as e:
        print(f"[{name}] 최신순 수집 오류: {e}")

    # 2. 유용순(MOST_RELEVANT) 추가 수집
    if len(all_revs) < target_count:
        try:
            batch, _ = reviews(
                app_id,
                lang=lang,
                country=country,
                sort=Sort.MOST_RELEVANT,
                count=min(target_count - len(all_revs), 1000)
            )
            added_rel = 0
            for r in batch:
                rid = r.get("reviewId")
                if rid and rid not in seen_ids:
                    seen_ids.add(rid)
                    at_val = r.get("at")
                    if at_val and at_val.year < cutoff_year:
                        continue
                    raw_content = r.get("content", "")
                    if not raw_content:
                        continue
                    
                    date_str = at_val.strftime("%Y-%m-%d %H:%M:%S") if isinstance(at_val, datetime) else str(at_val)
                    
                    cleaned_rec = {
                        "review_id": rid,
                        "app_name": name,
                        "app_id": app_id,
                        "score": r.get("score"),
                        "thumbs_up_count": r.get("thumbsUpCount", 0),
                        "date": date_str,
                        "review_text": mask_pii(raw_content),
                        "developer_reply": mask_pii(r.get("replyContent") or ""),
                        "app_version": r.get("reviewCreatedVersion") or ""
                    }
                    all_revs.append(cleaned_rec)
                    added_rel += 1
            print(f"  - [MOST_RELEVANT] +{added_rel}건 (누적 {len(all_revs)}건)")
        except Exception as e:
            print(f"[{name}] 유용순 수집 오류: {e}")
            
    print(f"[{name}] 총 {len(all_revs)}건 수집 완료!")
    return all_revs

def main():
    total_reviews_all_apps = []
    
    for target in TARGET_APPS:
        app_revs = collect_app_reviews(target, target_count=15000)
        total_reviews_all_apps.extend(app_revs)
        
        # Save individual app raw JSON
        app_json_path = OUTPUT_DIR / f"{target['name']}_raw_reviews.json"
        with open(app_json_path, "w", encoding="utf-8") as f:
            json.dump(app_revs, f, ensure_ascii=False, indent=2)
            
        # Save individual app CSV for easy viewing in Excel/Pandas
        app_csv_path = OUTPUT_DIR / f"{target['name']}_raw_reviews.csv"
        if app_revs:
            keys = list(app_revs[0].keys())
            with open(app_csv_path, "w", encoding="utf-8-sig", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=keys)
                writer.writeheader()
                writer.writerows(app_revs)
                
    # Save combined JSONL containing all raw reviews
    combined_jsonl_path = OUTPUT_DIR / "all_benchmark_reviews_raw.jsonl"
    with open(combined_jsonl_path, "w", encoding="utf-8") as f:
        for r in total_reviews_all_apps:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            
    print(f"\n🎉 [전체 완료] 총 {len(total_reviews_all_apps)}건의 원천 리뷰 데이터를 {OUTPUT_DIR} 에 저장했습니다!")

if __name__ == "__main__":
    main()
