"""DCX 페인포인트 수집기 (보배드림 공개 게시판, Scrapling Fetcher).

규칙: robots.txt 허용 URL만 요청 / 요청 간 지연 / 로그인·우회 없음 /
작성자·닉네임 미수집 / 전화·이메일·차량번호 마스킹.
사용: python collect.py --pages 3 --max-posts 60   |  --report  |  --selftest
"""
import argparse, csv, json, re, sys, time
from collections import Counter
from datetime import date
from html import unescape
from pathlib import Path
from urllib.parse import urlencode
from urllib.robotparser import RobotFileParser

from scrapling.fetchers import Fetcher

BASE = "https://www.bobaedream.co.kr"
SOURCE = "보배드림"  # 저장 경로: data/<유형>/<출처>/posts.jsonl. 새 소스는 별도 모듈로 추가
BOARDS = {"national": "국산차", "import": "수입차"}  # 시승기(사진 위주)·뉴스·자유게시판은 잡음이 커서 제외
DELAY = 2.0  # 초. shortcut: 고정 지연, 대량 수집 시 적응형 레이트리밋으로 교체
SEARCH = BASE + "/board/bulletin/list.php"  # 게시판 내 검색(제목+내용), 결과는 최신순
MONTHS = 24  # 수집 기간: 오늘 기준 최근 N개월만 (오래된 글은 현재 고객 불편과 어긋남)
# 게시판 검색어. 공백이 있으면 사이트가 OR 검색을 해서 잡음이 늘므로 붙여 쓴 구체 단어만 쓴다.
# 정밀도는 저장 전 relevance() 필터가 맡는다. 공모 분야: 인카 엔터테인먼트·안전 운전·패밀리/동승자 케어.
KEYWORDS = [
    "졸음운전", "졸음쉼터", "장거리운전", "동승자", "가족여행", "아이들",     # 졸음·지루함·동승자(대화모드)
    "휴게소", "경유지", "여행코스", "드라이브코스", "블랙박스", "빌트인캠",   # 경유지·기록(스탬프)
    "운전점수", "안전운전", "급가속", "급제동", "방향지시등", "블루멤버스",   # 운전 매너·보상
    "충전대기", "충전소", "전기차여행", "차박",                              # 전기차 여행·차박
]
DATA = Path(__file__).parent / "data"
TYPE_PRIORITY = ["차박", "전기차", "자동차여행"]  # 복수 주제 글은 앞선 유형 폴더에 저장, 나머지는 themes 필드에 남김
DEFAULT_TYPE = "국내자동차"

THEMES = {
    "전기차": ["전기차", "EV", "충전", "아이오닉", "EV6", "EV9", "테슬라", "전비", "배터리", "V2L"],
    "차박": ["차박", "캠핑", "오토캠핑", "에어매트", "평탄화", "릴선"],
    "자동차여행": ["여행", "휴게소", "드라이브", "장거리", "고속도로", "귀성", "로드트립", "졸음", "경유"],
}
PAIN = ["불편", "아쉽", "불만", "문제", "고장", "졸", "심심", "지루", "필요", "있으면 좋", "왜 없", "개선", "짜증", "스트레스"]
FEATURES = {  # 앱 기능 가설 <- 키워드
    "대화모드/졸음방지": ["졸음", "졸려", "졸리", "심심", "지루", "혼자", "장거리"],
    "경유지·스탬프·기록": ["휴게소", "경유", "맛집", "명소", "기록", "추억", "블랙박스", "사진"],
    "운전점수·마일리지": ["급가속", "급제동", "깜빡이", "방향지시", "양보", "매너", "연비", "보험", "할인"],
    "충전(EV)": ["충전소", "충전기", "충전", "급속", "완속", "대기"],
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
    return date(y, m + 1, min(today.day, 28))  # shortcut: 말일 보정 없이 28일 상한, 컷오프 하루 오차는 무관


def parse_list_date(text: str, today: date, prev: date) -> date | None:
    """목록 날짜 칸 -> date. 'HH:MM'=오늘, 'MM/DD'=연도 없음(최신순이므로 prev보다 미래면 작년)."""
    t = text.strip()
    if re.fullmatch(r"\d{1,2}:\d{2}(:\d{2})?", t):
        return today
    try:
        if m := re.fullmatch(r"(\d{2})/(\d{2})", t):
            d = date(prev.year, int(m[1]), int(m[2]))
            return d if d <= prev else date(prev.year - 1, int(m[1]), int(m[2]))
        if m := re.fullmatch(r"(\d{4}|\d{2})[./-](\d{2})[./-](\d{2})", t):
            y = int(m[1]) + (2000 if len(m[1]) == 2 else 0)
            return date(y, int(m[2]), int(m[3]))
    except ValueError:
        return None
    return None


def hits(text: str, table: dict) -> list[str]:
    return [k for k, kws in table.items() if any(w.lower() in text.lower() for w in kws)]


def selftest():
    assert scrub("010-1234-5678 a@b.com 12가3456") == "[전화] [이메일] [차량번호]"
    assert hits("휴게소에서 충전 대기", FEATURES) == ["경유지·스탬프·기록", "충전(EV)"]
    assert hits("그랜저 후기", THEMES) == []
    T = date(2026, 11, 2)
    assert parse_list_date("21:30", T, T) == T
    assert parse_list_date("07/26", T, T) == date(2026, 7, 26)
    assert parse_list_date("12/30", T, date(2026, 1, 5)) == date(2025, 12, 30)  # 연도 넘김
    assert parse_list_date("11/03", T, T) == date(2025, 11, 3)                 # 오늘보다 미래 -> 작년
    assert parse_list_date("24.12.25", T, T) == date(2024, 12, 25)
    assert parse_list_date("21:54:16", T, T) == T and parse_list_date("20/04/14", T, T) == date(2020, 4, 14)  # 뽐뿌
    assert relevance({"title": "장거리 운전 졸음 어떻게 쫓나요", "body": ""})[0]
    assert not relevance({"title": "전기차 보조금 출시", "body": "휴게소 불편"})[0]       # 뉴스 성격 제목
    assert not relevance({"title": "아이오닉9 별로인가요?", "body": "고민 중입니다"})[0]  # '아이오닉'은 상황어 아님
    assert parse_list_date("쓰레기", T, T) is None and parse_list_date("13/45", T, T) is None
    assert months_ago(T, 24) == date(2024, 11, 2) and months_ago(date(2026, 1, 31), 2) == date(2025, 11, 28)
    print("selftest ok")


def allowed_checker(base=BASE):
    """robots.txt 판정. shortcut: urllib은 '*' 와일드카드를 몰라 클리앙처럼 와일드카드 규칙을 쓰는 사이트는
    잘못 '허용'으로 나온다 -> 새 소스를 붙일 때 robots.txt 원문을 직접 읽어 확인할 것."""
    rp = RobotFileParser()
    r = Fetcher.get(base + "/robots.txt", timeout=20)
    if r.status != 200:
        sys.exit(f"robots.txt 확인 실패({r.status}) - 수집 중단")
    rp.parse(r.body.decode("utf-8", "ignore").splitlines())
    return lambda url: rp.can_fetch("*", url)


def all_rows() -> list[dict]:
    return [json.loads(l) for p in DATA.glob("*/*/posts.jsonl")
            for l in p.read_text(encoding="utf-8").splitlines() if l]


def save(rec: dict, source: str = SOURCE) -> Path:
    t = next((x for x in TYPE_PRIORITY if x in rec["themes"]), DEFAULT_TYPE)
    p = DATA / t / source / "posts.jsonl"
    p.parent.mkdir(parents=True, exist_ok=True)
    with p.open("a", encoding="utf-8") as f:
        f.write(json.dumps(rec | {"type": t, "source": source}, ensure_ascii=False) + "\n")
    return p


REJECTED = DATA / "_rejected.txt"  # 관련성 필터에서 탈락한 글 id (재요청 방지)
SCENE = ["휴게소", "졸음", "졸려", "졸리", "장거리", "가족여행", "동승", "아이들", "아이랑", "아이와", "애들",
         "심심", "지루", "귀성", "경유지", "빌트인캠", "블랙박스", "충전대기", "충전 대기", "충전소", "충전 시간", "충전시간",
         "운전점수", "운전 점수", "안전운전", "급가속", "급제동", "방향지시등", "블루멤버스", "차박"]
NEED = ["불편", "아쉽", "짜증", "힘들", "불만", "스트레스", "지루", "심심", "졸려", "졸음", "피곤", "있으면 좋", "있었으면",
        "했으면", "개선", "왜 없", "부족", "없어서"]  # '필요·방법·추천·어떻게'처럼 흔한 말은 오탐이 커서 제외
NEWS = ["점유율", "판매량", "보조금", "출시", "공개", "주가", "세액", "공제", "순위", "관세", "실적", "영업이익", "사전계약", "풀체인지"]


def relevance(rec: dict):
    """서비스 관련성 1차 필터: 상황어 AND 불편·니즈 표현이 본문에 있고, 제목이 뉴스·시장 성격이 아닐 것.
    shortcut: 키워드 규칙이라 오탐이 남는다 -> 수집 후 LLM 판정 단계로 보완."""
    t = rec["title"] + " " + rec["body"][:2000]
    scene, need = [w for w in SCENE if w in t], [w for w in NEED if w in t]
    return bool(scene and need and not any(w in rec["title"] for w in NEWS)), scene, need


def reject(rid: str):
    DATA.mkdir(exist_ok=True)
    with REJECTED.open("a", encoding="utf-8") as f:
        f.write(rid + "\n")


def seen_ids() -> set:
    rej = set(REJECTED.read_text(encoding="utf-8").split()) if REJECTED.exists() else set()
    return {r["id"] for r in all_rows()} | rej


def refilter():
    """기존 수집분을 relevance()로 재분류. 원본은 data/_원본백업.jsonl에 보존한 뒤 관련 글만 남긴다."""
    bak = DATA / "_원본백업.jsonl"
    if bak.exists():  # 이미 백업이 있으면 그것을 원본으로 다시 분류(기준을 바꿔 여러 번 돌려도 원본 유지)
        rows = [json.loads(l) for l in bak.read_text(encoding="utf-8").splitlines() if l]
    else:
        rows = all_rows()
        bak.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    for p in DATA.glob("*/*/posts.jsonl"):
        p.unlink()
    REJECTED.unlink(missing_ok=True)
    keep = 0
    for r in rows:
        ok, r["scene"], r["need"] = relevance(r)
        if ok: save(r); keep += 1
        else: reject(r["id"])
    return keep, len(rows)


def parse_post(url, board, title):
    time.sleep(DELAY)
    v = Fetcher.get(url, timeout=20)
    if v.status != 200 or not v.css(".bodyCont"):
        return None
    head = v.css(".writerProfile")[0].get_all_text(strip=True) if v.css(".writerProfile") else ""
    date = re.search(r"\d{4}\.\d{2}\.\d{2}", head)
    views = re.search(r"조회\s*([\d,]+)", head.replace("\n", " "))
    body = v.css(".bodyCont")[0].get_all_text(separator="\n", strip=True)
    comments = list(dict.fromkeys(  # best 댓글 중복 제거. 작성자(dt)는 읽지 않음
        d.get_all_text(strip=True) for d in v.css("#cmt_list dd") if d.get_all_text(strip=True)))
    return {
        "board": board, "title": scrub(title), "body": scrub(body)[:4000],
        "comments": [scrub(c)[:500] for c in comments[:50]],
        "date": date.group(0) if date else "", "views": int(views.group(1).replace(",", "")) if views else 0,
    }


def list_rows(url):
    """목록/검색 결과 한 페이지의 일반 글 행 (공지·베스트 제외)."""
    return [r for r in Fetcher.get(url, timeout=20).css("tbody tr")
            if r.css("a.bsubject") and "best" not in (r.attrib.get("class") or "")]


def collect(max_posts, max_pages, per_query=150):
    today = date.today()
    cutoff = months_ago(today, MONTHS)
    cutoff_s = cutoff.strftime("%Y.%m.%d")
    ok, done = allowed_checker(), seen_ids()
    print(f"컷오프: {cutoff_s} 이후만 수집 | 키워드 {len(KEYWORDS)}개 x 게시판 {len(BOARDS)}개", flush=True)
    n = 0
    have = Counter((r["id"].split("-")[0], r.get("found_by")) for r in all_rows())  # (게시판, 키워드)별 기존 건수
    for kw in KEYWORDS:  # 키워드 우선 순회: 중간에 멈춰도 키워드가 골고루 쌓인다
        for code, name in BOARDS.items():
            prev, first_prev, q_n = today, None, have[(code, kw)]
            if q_n >= per_query:
                continue
            for pg in range(1, max_pages + 1):
                q = {"code": code, "s_select": "Body", "s_key": kw, "type": "list", "pagescale": 30, "page": pg}
                s_url = f"{SEARCH}?{urlencode(q)}"
                if not ok(s_url):
                    print("robots 불허:", code, kw); break
                time.sleep(DELAY)
                rows = list_rows(s_url)
                if not rows:
                    break
                first = rows[0].css("a.bsubject")[0].attrib.get("href")
                if first == first_prev:
                    break  # 마지막 페이지 이후 같은 페이지가 반복되면 종료
                first_prev, last = first, None
                for r in rows:
                    tds = r.css("td")
                    d = parse_list_date(tds[3].get_all_text(strip=True), today, prev) if len(tds) > 3 else None
                    if d is None:
                        continue
                    prev = last = d
                    if d < cutoff:
                        continue
                    a = r.css("a.bsubject")[0]
                    m = re.search(r"No=(\d+)", a.attrib.get("href", ""))
                    if not m or f"{code}-{m.group(1)}" in done:
                        continue
                    url = f"{BASE}/view?code={code}&No={m.group(1)}"
                    if not ok(url):
                        continue
                    rec = parse_post(url, name, a.get_all_text(strip=True))
                    if not rec or (rec["date"] and rec["date"] < cutoff_s):
                        continue  # 본문 날짜로 이중 검사
                    full = " ".join([rec["title"], rec["body"], *rec["comments"]])
                    rec |= {"id": f"{code}-{m.group(1)}", "url": url, "found_by": kw,
                            "themes": hits(full, THEMES), "features": hits(full, FEATURES)}
                    done.add(rec["id"])
                    ok_r, rec["scene"], rec["need"] = relevance(rec)
                    if not ok_r:
                        reject(rec["id"]); continue
                    save(rec); n += 1; q_n += 1
                    print(f"[{n}] {rec['date']} {rec['board']} | {rec['title'][:36]} | {kw} -> {rec['themes']}", flush=True)
                    if n >= max_posts:
                        return n
                    if q_n >= per_query:
                        break
                if q_n >= per_query or last is None or last < cutoff:
                    break  # 이 쿼리는 상한 또는 컷오프 도달
    return n


PPOM = "https://www.ppomppu.co.kr/zboard"  # 뽐뿌 자동차포럼(id=car). robots: /zboard/ 허용, 사이트 전체 검색(/search_bbs.php)은 금지


def parse_ppomppu(url, title, d):
    time.sleep(DELAY)
    v = Fetcher.get(url, timeout=20)
    body = v.css("td.board-contents")
    if v.status != 200 or not body:
        return None
    html, comments = v.html_content, []
    if (i := html.find("initialCommentData =")) >= 0:  # 댓글은 페이지 내 JSON. 작성자(name)는 읽지 않음
        try:
            data, _ = json.JSONDecoder().raw_decode(html[html.find("{", i):])
            comments = [unescape(re.sub(r"<[^>]+>", " ", c.get("memo", ""))).strip() for c in data.get("comments", [])]
        except ValueError:
            pass
    return {
        "board": "자동차포럼", "title": scrub(" ".join(title.split())), "body": scrub(body[0].get_all_text(separator="\n", strip=True))[:4000],
        "comments": [scrub(c)[:500] for c in dict.fromkeys(comments) if c][:50],
        "date": d.strftime("%Y.%m.%d"), "views": 0,
    }


def collect_ppomppu(max_posts, max_pages, per_query=150):
    today = date.today()
    cutoff = months_ago(today, MONTHS)
    ok, done = allowed_checker("https://www.ppomppu.co.kr"), seen_ids()
    print(f"[뽐뿌] 컷오프: {cutoff:%Y.%m.%d} 이후 | 키워드 {len(KEYWORDS)}개", flush=True)
    n = 0
    have = Counter((r["id"].split("-")[0], r.get("found_by")) for r in all_rows())
    for kw in KEYWORDS:
        prev, first_prev, q_n = today, None, have[("ppomppu", kw)]
        if q_n >= per_query:
            continue
        for pg in range(1, max_pages + 1):
            s_url = f"{PPOM}/zboard.php?" + urlencode({"id": "car", "search_type": "sub_memo", "keyword": kw, "page_num": 30, "page": pg})
            if not ok(s_url):
                print("robots 불허:", kw); break
            time.sleep(DELAY)
            rows = [r for r in Fetcher.get(s_url, timeout=20).css("tr")
                    if len(r.css("td")) > 5 and re.fullmatch(r"\d+", r.css("td")[0].get_all_text(strip=True))
                    and r.css("a[href*='view.php?id=car']")]
            if not rows or rows[0].css("td")[0].get_all_text(strip=True) == first_prev:
                break
            first_prev, last = rows[0].css("td")[0].get_all_text(strip=True), None
            for r in rows:
                tds = r.css("td")
                d = parse_list_date(tds[5].get_all_text(strip=True), today, prev)
                if d is None:
                    continue
                prev = last = d
                no = tds[0].get_all_text(strip=True)
                if d < cutoff or f"ppomppu-car-{no}" in done:
                    continue
                links = r.css("a[href*='view.php?id=car']")
                a = next((x for x in links if x.get_all_text(strip=True)), links[0])  # 첫 링크는 빈 이미지 링크
                url = f"{PPOM}/view.php?id=car&no={no}"
                if not ok(url):
                    continue
                rec = parse_ppomppu(url, a.get_all_text(strip=True), d)
                if not rec:
                    continue
                full = " ".join([rec["title"], rec["body"], *rec["comments"]])
                rec |= {"id": f"ppomppu-car-{no}", "url": url, "found_by": kw,
                        "themes": hits(full, THEMES), "features": hits(full, FEATURES)}
                done.add(rec["id"])
                ok_r, rec["scene"], rec["need"] = relevance(rec)
                if not ok_r:
                    reject(rec["id"]); continue
                save(rec, source="뽐뿌"); n += 1; q_n += 1
                print(f"[뽐뿌 {n}] {rec['date']} | {rec['title'][:36]} | {kw} -> {rec['themes']}", flush=True)
                if n >= max_posts:
                    return n
                if q_n >= per_query:
                    break
            if q_n >= per_query or last is None or last < cutoff:
                break
    return n


def export(path=DATA / "수집결과.csv"):
    """사람이 보기 위한 표. 엑셀에서 바로 열린다(utf-8-sig). 원본은 posts.jsonl."""
    rows = sorted(all_rows(), key=lambda r: r["date"], reverse=True)
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["날짜", "유형", "출처", "게시판", "제목", "본문(앞 300자)", "댓글수", "조회수", "주제", "기능태그", "검색키워드", "URL"])
        for r in rows:
            w.writerow([r["date"], r["type"], r["source"], r["board"], r["title"], r["body"][:300].replace("\n", " "),
                        len(r["comments"]), r["views"], ", ".join(r["themes"]), ", ".join(r["features"]), r.get("found_by", ""), r["url"]])
    return len(rows), path


def report():
    rows = all_rows()
    print(f"총 {len(rows)}건", dict(Counter(f"{r['type']}/{r['source']}" for r in rows)))
    for key in ("themes", "features"):
        print(f"\n[{key}]", dict(Counter(x for r in rows for x in r[key])))
    for feat in FEATURES:
        print(f"\n== {feat} 대표 글 ==")
        for r in sorted((r for r in rows if feat in r["features"]), key=lambda r: -r["views"])[:3]:
            print(f"- ({r['views']:,}뷰) {r['title'][:50]}  {r['url']}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-pages", type=int, default=100, help="쿼리(게시판x키워드)당 최대 페이지")
    ap.add_argument("--max-posts", type=int, default=30)
    ap.add_argument("--per-query", type=int, default=150, help="게시판x키워드당 최대 건수(키워드 쏠림 방지)")
    ap.add_argument("--source", choices=["bobaedream", "ppomppu"], default="bobaedream")
    ap.add_argument("--report", action="store_true")
    ap.add_argument("--refilter", action="store_true", help="기존 수집분을 관련성 필터로 재분류(원본은 data/_원본백업.jsonl)")
    ap.add_argument("--export", action="store_true", help="data/수집결과.csv 생성(엑셀용)")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest: selftest()
    elif a.refilter: print("관련 글 %d건 / 기존 %d건" % refilter())
    elif a.report: report()
    elif a.export: print("CSV 저장: %d건 -> %s" % export())
    else:
        run = collect_ppomppu if a.source == "ppomppu" else collect
        print("저장:", run(a.max_posts, a.max_pages, a.per_query), "건 ->", DATA)
