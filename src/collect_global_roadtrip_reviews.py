"""
Global Road Trip & EV Driver Raw Review Collector.
Collects raw unsummarized overseas user reviews for:
1. Roadtrippers (com.roadtrippers) - Global #1 Road Trip waypoint/stopover planner
2. Wanderlog (com.wanderlog.android) - North America #1 Road trip & travel planner
3. A Better Routeplanner - ABRP (com.iternio.abrpapp) - Global #1 EV road trip charging route planner
4. PlugShare (com.xatori.Plugshare) - Global #1 EV charging station map & community

Saves raw reviews to data/벤치마크_유사서비스/원천리뷰데이터/해외_글로벌_로드트립_EV/
"""
import os
import sys
import json
import csv
import re
from pathlib import Path
from datetime import datetime, timezone
from google_play_scraper import reviews, Sort

sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "data" / "벤치마크_유사서비스" / "원천리뷰데이터" / "해외_글로벌_로드트립_EV"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

TARGET_APPS = [
    {
        "name": "Roadtrippers_로드트리퍼스",
        "app_id": "com.roadtrippers",
        "lang": "en",
        "country": "us",
        "target_count": 5000,
        "description": "글로벌 1위 로드트립 경유지/명소 플래너 - 장거리 운전 피로, 경로 탐색 VOC"
    },
    {
        "name": "Wanderlog_원더로그",
        "app_id": "com.wanderlog.android",
        "lang": "en",
        "country": "us",
        "target_count": 5000,
        "description": "북미 1위 자동차 여행/동선 플래너 - 여행 기록, 오프라인 맵, 경유지 불편 VOC"
    },
    {
        "name": "ABRP_일렉트릭_루트플래너",
        "app_id": "com.iternio.abrpapp",
        "lang": "en",
        "country": "us",
        "target_count": 5000,
        "description": "글로벌 1위 전기차(EV) 고속도로 장거리 경로/충전 플래너 - 배터리 소모 예측, 충전 대기 VOC"
    },
    {
        "name": "PlugShare_플러그셰어",
        "app_id": "com.xatori.Plugshare",
        "lang": "en",
        "country": "us",
        "target_count": 5000,
        "description": "글로벌 1위 전기차 충전소 커뮤니티 - 충전기 고장, 충전소 편의시설 부족, 체류 시간 불편 VOC"
    }
]

PII_PATTERNS = [
    (re.compile(r"01\d[-.\s]?\d{3,4}[-.\s]?\d{4}"), "[전화]"),
    (re.compile(r"\b\d{3}[-.\s]?\d{3}[-.\s]?\d{4}\b"), "[전화]"),
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"), "[이메일]"),
]

def mask_pii(text: str) -> str:
    if not text:
        return ""
    for rx, rep in PII_PATTERNS:
        text = rx.sub(rep, text)
    return text

def collect_app_reviews(target: dict) -> list:
    name = target["name"]
    app_id = target["app_id"]
    lang = target["lang"]
    country = target["country"]
    target_count = target.get("target_count", 2000)
    
    print(f"\n[수집 시작] {name} ({app_id}) - 최대 목표: {target_count}건...")
    
    cutoff_year = datetime.now().year - 2  # 최근 24개월 (2024년 이후)
    all_collected = []
    seen_ids = set()
    
    # 1. 최신순(NEWEST) 대량 수집 (페이지네이션)
    try:
        current_token = None
        while len(all_collected) < target_count:
            fetch_count = min(target_count - len(all_collected), 1000)
            if current_token is None:
                res, current_token = reviews(
                    app_id,
                    lang=lang,
                    country=country,
                    sort=Sort.NEWEST,
                    count=fetch_count
                )
            else:
                res, current_token = reviews(app_id, continuation_token=current_token)
                
            if not res:
                break
                
            added_in_batch = 0
            reached_cutoff = False
            for r in res:
                rid = r.get("reviewId")
                if rid and rid not in seen_ids:
                    seen_ids.add(rid)
                    dt = r.get("at")
                    if dt and dt.year < cutoff_year:
                        reached_cutoff = True
                        break
                    all_collected.append(r)
                    added_in_batch += 1
                    
            print(f"  - 최신순 수집 배치: +{added_in_batch}건 (누적 {len(all_collected)}건)")
            if reached_cutoff or current_token is None or added_in_batch == 0:
                break
    except Exception as e:
        print(f"  - 최신순 수집 오류: {e}")

    # 2. 유용순(MOST_RELEVANT) 추가 수집 (핵심 불만 및 페인포인트 추가 확보)
    if len(all_collected) < target_count:
        needed = target_count - len(all_collected)
        try:
            res_helpful, _ = reviews(
                app_id,
                lang=lang,
                country=country,
                sort=Sort.MOST_RELEVANT,
                count=min(needed, 1000)
            )
            added_helpful = 0
            for r in res_helpful:
                rid = r.get("reviewId")
                if rid and rid not in seen_ids:
                    seen_ids.add(rid)
                    dt = r.get("at")
                    if dt and dt.year >= cutoff_year:
                        all_collected.append(r)
                        added_helpful += 1
            print(f"  - 유용순(Relevant) 추가 수집: +{added_helpful}건 (누적 {len(all_collected)}건)")
        except Exception as e:
            print(f"  - 유용순 수집 오류: {e}")
            
    # 정제 및 표준화
    cleaned_records = []
    for r in all_collected:
        clean_content = mask_pii(r.get("content", "").strip())
        if not clean_content:
            continue
            
        dt = r.get("at")
        date_str = dt.strftime("%Y-%m-%d %H:%M:%S") if dt else ""
        
        reply_content = r.get("replyContent")
        if reply_content:
            reply_content = mask_pii(reply_content.strip())
            
        rec = {
            "review_id": r.get("reviewId"),
            "app_name": name,
            "app_id": app_id,
            "description": target["description"],
            "score": r.get("score"),
            "date": date_str,
            "year": dt.year if dt else None,
            "content": clean_content,
            "thumbs_up_count": r.get("thumbsUpCount", 0),
            "app_version": r.get("reviewCreatedVersion", ""),
            "developer_reply": reply_content or "",
            "language": lang,
            "country": country,
            "source": "Google Play Store (Global Raw Reviews)"
        }
        cleaned_records.append(rec)
        
    print(f"  => 최종 유효 수집 건수: {len(cleaned_records)}건")
    return cleaned_records

def main():
    print("=" * 70)
    print("🚀 해외 글로벌 로드트립 & EV 서비스 원천 리뷰 수집기 시작")
    print("   대상: Roadtrippers, Wanderlog, ABRP, PlugShare")
    print("=" * 70)
    
    all_global_reviews = []
    summary_stats = []
    
    for target in TARGET_APPS:
        recs = collect_app_reviews(target)
        if not recs:
            continue
            
        all_global_reviews.extend(recs)
        
        # 개별 JSON 저장
        json_path = OUTPUT_DIR / f"{target['name']}_raw_reviews.json"
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(recs, f, ensure_ascii=False, indent=2)
            
        # 개별 CSV 저장 (UTF-8-SIG for Excel compatibility)
        csv_path = OUTPUT_DIR / f"{target['name']}_raw_reviews.csv"
        with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=[
                "review_id", "app_name", "app_id", "score", "date", "year",
                "content", "thumbs_up_count", "app_version", "developer_reply", "source"
            ], extrasaction="ignore")
            writer.writeheader()
            writer.writerows(recs)
            
        # 평점 분포
        scores = [r["score"] for r in recs if r["score"] is not None]
        avg_score = round(sum(scores) / len(scores), 2) if scores else 0
        neg_count = sum(1 for s in scores if s in [1, 2])
        
        summary_stats.append({
            "앱 이름": target["name"],
            "패키지": target["app_id"],
            "수집 건수": len(recs),
            "평균 평점": avg_score,
            "불만/부정(1-2점)": f"{neg_count}건 ({round(neg_count/len(scores)*100, 1) if scores else 0}%)",
            "JSON 경로": str(json_path.relative_to(BASE_DIR)),
            "CSV 경로": str(csv_path.relative_to(BASE_DIR)),
        })
        
    # 통합 JSONL 저장
    jsonl_path = OUTPUT_DIR / "all_global_roadtrip_reviews_raw.jsonl"
    with open(jsonl_path, "w", encoding="utf-8") as f:
        for r in all_global_reviews:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            
    print("\n" + "=" * 70)
    print("📊 수집 완료 종합 결과")
    print("=" * 70)
    for stat in summary_stats:
        print(f"[{stat['앱 이름']}] {stat['수집 건수']}건 수집 완료 | 평균 평점: {stat['평균 평점']}★ | 부정/개선요구: {stat['불만/부정(1-2점)']}")
        
    print(f"\n총 글로벌 원천 리뷰 수집량: {len(all_global_reviews)}건")
    print(f"통합 JSONL 저장 완료: {jsonl_path.relative_to(BASE_DIR)}")

if __name__ == "__main__":
    main()

