"""
Ppomppu '자동차포럼' (id=car) crawler using Scrapling Fetcher and Selector.
Handles EUC-KR encoding and extracts post title, content, and comments.
"""
import time
import re
import urllib.parse
from typing import List, Dict
from scrapling import Fetcher
from scrapling.parser import Selector

class PpomppuCrawler:
    BASE_URL = "https://www.ppomppu.co.kr/zboard"
    
    def __init__(self, board_id: str = "car"):
        self.board_id = board_id
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer": "https://www.ppomppu.co.kr/zboard/zboard.php?id=car",
        }

    def search_posts(self, query: str, max_pages: int = 1) -> List[Dict]:
        """Search posts within car board with euc-kr encoding."""
        results = []
        try:
            encoded_q = urllib.parse.quote(query, encoding="euc-kr")
        except Exception:
            encoded_q = urllib.parse.quote(query)

        for page in range(1, max_pages + 1):
            url = f"{self.BASE_URL}/zboard.php?id={self.board_id}&page={page}&search_type=sub_memo&keyword={encoded_q}"
            try:
                resp = Fetcher.get(url, headers=self.headers)
                if resp.status != 200 or not resp.body:
                    continue
                
                html = resp.body.decode("euc-kr", errors="replace")
                tree = Selector(html)
                
                # Links to view.php?id=car
                links = tree.css("a")
                seen_nos = set()
                
                for a in links:
                    href = a.attrib.get("href", "")
                    if "view.php?id=car" in href and "no=" in href:
                        # Extract post no
                        m = re.search(r"no=(\d+)", href)
                        if not m:
                            continue
                        post_no = m.group(1)
                        if post_no in seen_nos:
                            continue
                        
                        title_text = a.get_all_text().strip()
                        # Skip empty title links or numeric pagination links
                        if not title_text or title_text.isdigit() or len(title_text) < 2:
                            continue
                        
                        seen_nos.add(post_no)
                        full_url = f"{self.BASE_URL}/view.php?id={self.board_id}&no={post_no}"
                        results.append({
                            "source": "뽐뿌_자동차포럼",
                            "query": query,
                            "post_no": post_no,
                            "title": title_text,
                            "url": full_url,
                            "date": ""
                        })
                time.sleep(0.5)
            except Exception as e:
                print(f"[Ppomppu] Error searching {query} page {page}: {e}")
                
        return results

    def fetch_post_detail(self, post_info: Dict) -> Dict:
        """Fetch article body and comments from Ppomppu."""
        url = post_info["url"]
        try:
            resp = Fetcher.get(url, headers=self.headers)
            if resp.status == 200 and resp.body:
                html = resp.body.decode("euc-kr", errors="replace")
                tree = Selector(html)
                
                # Clean title from title tag if needed
                title_tags = tree.css("title")
                if title_tags:
                    t_raw = title_tags[0].get_all_text().strip()
                    if "::" in t_raw:
                        post_info["title"] = t_raw.split("::")[-1].strip()
                
                # Content
                contents = tree.css("td.board-contents")
                if contents:
                    post_info["content"] = contents[0].get_all_text().strip()
                else:
                    post_info["content"] = ""
                
                # Comments
                comments = []
                for node in tree.css(".comment_memo_text, div.comment_content, td.han"):
                    txt = node.get_all_text().strip()
                    if txt and len(txt) > 3 and txt not in comments:
                        comments.append(txt)
                post_info["comments"] = comments[:10]
                return post_info
        except Exception as e:
            print(f"[Ppomppu] Error fetching detail for {url}: {e}")
            
        post_info["content"] = ""
        post_info["comments"] = []
        return post_info

