"""
Reddit DCX Painpoint Collector for Hyundai PLEOS In-Car App Contest.
Uses official PRAW (Python Reddit API Wrapper) library.
Strictly adheres to GEMINI.md rules:
- Uses official Reddit OAuth API via PRAW.
- Reads credentials from environment variables: REDDIT_CLIENT_ID, REDDIT_CLIENT_SECRET, REDDIT_USER_AGENT.
- Masks PII via scrub(), captures no author fields.
- 24-month cutoff constraint.
- Saves to data/<type>/Reddit/posts.jsonl.
"""
import os
import sys
import json
import time
from datetime import datetime, date, timezone
from pathlib import Path
import praw

# Import helper functions from collect.py
sys.path.append(str(Path(__file__).parent))
from collect import scrub, months_ago, hits, save, seen_ids, THEMES, FEATURES, MONTHS, DATA

REDDIT_SUBREDDITS = {
    "roadtrip": "자동차여행",
    "electricvehicles": "전기차",
    "Ioniq5": "전기차",
    "Hyundai": "국내자동차",
    "carcamping": "차박",
    "vandwellers": "차박"
}

ENGLISH_KEYWORDS = {
    "roadtrip": ["drowsy driving", "boredom", "rest area", "scenic drive", "audio guide", "passenger bored", "road trip fatigue"],
    "electricvehicles": ["charging wait", "charger broken", "long road trip", "charging dwell time", "in car entertainment"],
    "Ioniq5": ["navigation", "charging curve", "road trip", "infotainment", "built-in cam", "blue link"],
    "Hyundai": ["infotainment", "bluelink", "driving score", "long road trip", "navigation update"],
    "carcamping": ["rest area camping", "overnight parking", "in car entertainment", "stealth camping", "car mattress"],
    "vandwellers": ["rest stop overnight", "manners", "boredom", "power supply", "v2l"]
}

def get_reddit_instance():
    client_id = os.environ.get("REDDIT_CLIENT_ID")
    client_secret = os.environ.get("REDDIT_CLIENT_SECRET")
    user_agent = os.environ.get("REDDIT_USER_AGENT", "PleosDcxCollector/1.0 (by /u/hyundai_app_contest)")
    
    if not client_id or not client_secret:
        return None
        
    return praw.Reddit(
        client_id=client_id,
        client_secret=client_secret,
        user_agent=user_agent
    )

def collect_reddit(max_posts_per_sub: int = 50):
    reddit = get_reddit_instance()
    if not reddit:
        print("\n[!] Reddit 수집 안내: REDDIT_CLIENT_ID 및 REDDIT_CLIENT_SECRET 환경변수가 필요합니다.")
        print("    GEMINI.md 절대 규칙에 따라 공식 PRAW / OAuth API 키가 설정되면 즉시 수집할 수 있습니다.")
        print("    발급 방법: https://www.reddit.com/prefs/apps 에서 'script' 타입 앱 등록")
        return 0
        
    today = date.today()
    cutoff = months_ago(today, MONTHS)
    cutoff_ts = datetime(cutoff.year, cutoff.month, cutoff.day, tzinfo=timezone.utc).timestamp()
    done = seen_ids()
    
    total_saved = 0
    print(f"\n[Reddit (PRAW)] 공식 API 수집 시작 (컷오프: {cutoff:%Y.%m.%d} 이후)")
    
    for sub_name, default_type in REDDIT_SUBREDDITS.items():
        sub_count = 0
        keywords = ENGLISH_KEYWORDS.get(sub_name, ["road trip", "in car entertainment"])
        print(f"\n--> r/{sub_name} 수집 중...")
        
        try:
            subreddit = reddit.subreddit(sub_name)
            for kw in keywords:
                # Search using official PRAW search
                search_results = subreddit.search(kw, sort="new", limit=25)
                time.sleep(2.0)  # GEMINI.md rate limit rule (2s)
                
                for post in search_results:
                    if post.created_utc < cutoff_ts:
                        continue
                        
                    global_id = f"reddit-{sub_name}-{post.id}"
                    if global_id in done:
                        continue
                        
                    title = scrub(post.title or "")
                    selftext = scrub(post.selftext or "")[:4000]
                    post_date = datetime.fromtimestamp(post.created_utc, timezone.utc).strftime("%Y.%m.%d")
                    
                    # Top comments (up to 10 comments)
                    comments = []
                    try:
                        post.comments.replace_more(limit=0)
                        for top_comment in post.comments[:10]:
                            c_body = scrub(top_comment.body or "")[:500]
                            if c_body and len(c_body) > 5:
                                comments.append(c_body)
                    except Exception:
                        pass
                        
                    full_text = f"{title} {selftext} {' '.join(comments)}"
                    
                    rec = {
                        "id": global_id,
                        "board": f"r/{sub_name}",
                        "title": title,
                        "body": selftext,
                        "comments": comments,
                        "date": post_date,
                        "views": post.score,  # Upvote score as engagement proxy
                        "url": f"https://www.reddit.com{post.permalink}",
                        "found_by": kw,
                        "themes": hits(full_text, THEMES) or [default_type],
                        "features": hits(full_text, FEATURES)
                    }
                    
                    save(rec, source="Reddit")
                    done.add(global_id)
                    total_saved += 1
                    sub_count += 1
                    print(f"[Reddit {total_saved}] {post_date} r/{sub_name} | {title[:36]} | {kw}", flush=True)
                    
                    if sub_count >= max_posts_per_sub:
                        break
                if sub_count >= max_posts_per_sub:
                    break
        except Exception as e:
            print(f"[Reddit Error] r/{sub_name} 수집 중 오류: {e}")
            
    print(f"\n🎉 [Reddit 완료] 총 {total_saved}건의 레딧 원천 게시글 및 댓글을 data/ 폴더에 저장했습니다.")
    return total_saved

if __name__ == "__main__":
    collect_reddit()
