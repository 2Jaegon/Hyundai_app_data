"""DCX 페인포인트 수집기 (아카라이브 자동차/캠핑 채널).

규칙: robots.txt(Allow: /) 준수 / 2초 지연 / 로그인·우회 없음 /
작성자·닉네임 미수집 / 전화·이메일·차량번호 마스킹 / 최근 24개월.
저장 경로: data/<유형>/아카라이브/posts.jsonl
"""
import argparse
import csv
import json
import re
import sys
import time
from collections import Counter
from datetime import date, datetime, timezone
from pathlib import Path
from urllib.parse import quote
import requests
from bs4 import BeautifulSoup

sys.stdout.reconfigure(encoding="utf-8")

BASE = "https://arca.live"
SOURCE = "아카라이브"
CHANNELS = {
    "vehicle": "자동차",
    "camping": "캠핑차박"
}
DELAY = 2.0  # 초
MONTHS = 24

KEYWORDS = [
    "졸음", "장거리", "휴게소", "드라이브", "차박", "충전", "네비", "인포테인먼트"
]

DATA = Path(__file__).parent / "data"
TYPE_PRIORITY = ["차박", "전기차", "자동차여행"]
DEFAULT_TYPE = "국내자동차"

THEMES = {
    "전기차": ["전기차", "EV", "충전", "아이오닉", "EV6", "EV9", "테슬라", "배터리", "V2L"],
    "차박": ["차박", "캠핑", "오토캠핑", "평탄화", "매트"],
    "자동차여행": ["여행", "휴게소", "드라이브", "장거리", "고속도로", "국도", "경유", "졸음"],
}
PAIN = ["불편", "아쉽", "불만", "문제", "고장", "졸", "심심", "지루", "필요", "왜 없", "개선", "짜증", "스트레스", "힘들"]
FEATURES = {
    "대화모드/졸음방지": ["졸음", "졸려", "졸리", "심심", "지루", "장거리", "피곤"],
    "경유지·스탬프·기록": ["휴게소", "경유", "맛집", "명소", "기록", "사진", "블박", "추억"],
    "운전점수·마일리지": ["급가속", "급제동", "깜빡이", "방향지시", "매너", "점수", "할인"],
    "충전(EV)": ["충전소", "충전기", "충전", "대기", "급속", "방전"],
    "차박·캠핑": ["차박", "캠핑", "평탄화", "매트", "V2L"],
}

PII = [
    (re.compile(r"01\d[-.\s]?\d{3,4}[-.\s]?\d{4}"), "[전화]"),
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"), "[이메일]"),
    (re.compile(r"\d{2,3}\s?[가-힣]\s?\d{4}"), "[차량번호]"),
]

def scrub(t: str) -> str:
    for rx, rep in PII:
        t = rx.sub(rep, t)
    return t

def months_ago(today: date, n: int) -> date:
    y, m = divmod(today.year * 12 + today.month - 1 - n, 12)
    return date(y, m + 1, min(today.day, 28))

def hits(text: str, words: list[str]) -> bool:
    return any(w in text for w in words)

def match_features(text: str) -> list[str]:
    return [feat for feat, kws in FEATURES.items() if hits(text, kws)]

def classify(text: str) -> tuple[str, list[str]]:
    matched = [cat for cat, kws in THEMES.items() if hits(text, kws)]
    primary = next((c for c in TYPE_PRIORITY if c in matched), DEFAULT_TYPE)
    return primary, matched

def parse_iso_date(dt_str: str) -> date | None:
    if not dt_str:
        return None
    try:
        # e.g. 2026-11-01T09:29:23.000Z
        clean_dt = dt_str.split(".")[0].replace("Z", "")
        dt = datetime.fromisoformat(clean_dt)
        return dt.date()
    except Exception:
        return None

def load_seen_ids() -> set[str]:
    seen = set()
    for p in DATA.rglob("posts.jsonl"):
        try:
            with open(p, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip():
                        try:
                            rec = json.loads(line)
                            seen.add(rec["id"])
                        except Exception:
                            pass
        except Exception:
            pass
    return seen

def save(rec: dict, source: str = SOURCE):
    p = DATA / rec["type"] / source / "posts.jsonl"
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")

def collect_arca(max_posts: int = 100, max_pages_per_kw: int = 2):
    print("=" * 70)
    print("🚀 아카라이브 자동차/캠핑 채널 DCX 페인포인트 수집기 시작")
    print("=" * 70)

    seen = load_seen_ids()
    print(f"기존 수집 ID 수: {len(seen)}건")

    today = date.today()
    cutoff = months_ago(today, MONTHS)
    print(f"수집 기준일: {today} | 24개월 컷오프: {cutoff}")

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    session = requests.Session()
    session.headers.update(headers)

    saved_count = 0
    channel_counts = Counter()
    type_counts = Counter()

    for ch_slug, ch_name in CHANNELS.items():
        if saved_count >= max_posts:
            break

        print(f"\n📂 채널: {ch_name} (b/{ch_slug})")

        for kw in KEYWORDS:
            if saved_count >= max_posts:
                break

            print(f"  🔍 키워드 검색: '{kw}'")

            for page in range(1, max_pages_per_kw + 1):
                if saved_count >= max_posts:
                    break

                search_url = f"{BASE}/b/{ch_slug}?target=title_content&keyword={quote(kw)}&p={page}"
                time.sleep(DELAY)

                try:
                    r = session.get(search_url, timeout=10)
                    if r.status_code != 200:
                        print(f"    - 페이지 {page} 응답 오류: {r.status_code}")
                        break
                except Exception as e:
                    print(f"    - 페이지 {page} 요청 실패: {e}")
                    break

                soup = BeautifulSoup(r.text, "html.parser")
                # 공지 제외하고 일반 글 추출
                article_rows = [
                    a for a in soup.select("a.vrow")
                    if not "notice" in a.get("class", []) and a.select_one(".col-title")
                ]

                if not article_rows:
                    break

                for a in article_rows:
                    if saved_count >= max_posts:
                        break

                    href = a.get("href", "")
                    # href 예: /b/vehicle/187742437?target=title_content...
                    m = re.search(rf"/b/{ch_slug}/(\d+)", href)
                    if not m:
                        continue
                    post_num = m.group(1)
                    post_id = f"arca-{ch_slug}-{post_num}"

                    if post_id in seen:
                        continue

                    # 날짜 확인
                    time_el = a.select_one("time")
                    dt_str = time_el.get("datetime") if time_el else ""
                    post_date = parse_iso_date(dt_str)
                    if post_date and post_date < cutoff:
                        continue

                    title_el = a.select_one(".col-title")
                    title = title_el.get_text(" ", strip=True) if title_el else ""
                    title = scrub(re.sub(r"\[\d+\]$", "", title).strip())

                    # 상세 본문 및 댓글 가져오기
                    post_url = f"{BASE}/b/{ch_slug}/{post_num}"
                    time.sleep(DELAY)

                    try:
                        resp_post = session.get(post_url, timeout=10)
                        if resp_post.status_code != 200:
                            continue
                    except Exception:
                        continue

                    post_soup = BeautifulSoup(resp_post.text, "html.parser")
                    body_el = post_soup.select_one(".article-body")
                    if not body_el:
                        continue

                    body_text = scrub(body_el.get_text("\n", strip=True))[:4000]
                    if len(body_text) < 20:
                        continue

                    # 페인포인트 또는 키워드 관련성 검증
                    full_text = title + " " + body_text
                    if not (hits(full_text, PAIN) or hits(full_text, KEYWORDS)):
                        continue

                    # 댓글 추출 (최대 30개)
                    comments = []
                    for c_el in post_soup.select(".comment-item .message"):
                        c_text = scrub(c_el.get_text(" ", strip=True))
                        if c_text and len(c_text) >= 5:
                            comments.append(c_text[:500])
                        if len(comments) >= 30:
                            break

                    primary_type, themes_matched = classify(full_text)
                    features_matched = match_features(full_text)

                    rec = {
                        "id": post_id,
                        "source": SOURCE,
                        "type": primary_type,
                        "board": ch_name,
                        "title": title,
                        "body": body_text,
                        "comments": comments,
                        "date": post_date.strftime("%Y.%m.%d") if post_date else today.strftime("%Y.%m.%d"),
                        "views": 0,
                        "url": post_url,
                        "found_by": kw,
                        "themes": themes_matched,
                        "features": features_matched
                    }

                    save(rec)
                    seen.add(post_id)
                    saved_count += 1
                    channel_counts[ch_name] += 1
                    type_counts[primary_type] += 1

                    print(f"    [{saved_count}/{max_posts}] [{primary_type}] {title[:35]} (댓글 {len(comments)}개)")

    print("\n" + "=" * 70)
    print("📊 아카라이브 수집 완료 결과")
    print("=" * 70)
    print(f"총 신규 수집 건수: {saved_count}건")
    for ch, cnt in channel_counts.items():
        print(f" - 채널 [{ch}]: {cnt}건")
    for tp, cnt in type_counts.items():
        print(f" - 유형 [{tp}]: {cnt}건")

    # 전체 CSV 갱신
    update_master_csv()

def update_master_csv():
    all_posts = []
    for p in DATA.rglob("posts.jsonl"):
        try:
            with open(p, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip():
                        all_posts.append(json.loads(line))
        except Exception:
            pass

    csv_path = DATA / "수집결과.csv"
    with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "id", "source", "type", "board", "date", "views",
            "found_by", "themes", "features", "url", "title", "body", "comments"
        ], extrasaction="ignore")
        writer.writeheader()
        for r in all_posts:
            r_copy = dict(r)
            r_copy["themes"] = ", ".join(r.get("themes", []))
            r_copy["features"] = ", ".join(r.get("features", []))
            r_copy["comments"] = f"[{len(r.get('comments', []))}개 댓글] " + " // ".join(r.get("comments", []))[:1000]
            writer.writerow(r_copy)

    print(f"통합 CSV 갱신 완료: {csv_path} (총 {len(all_posts)}건)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-posts", type=int, default=80)
    args = parser.parse_args()
    collect_arca(max_posts=args.max_posts)

