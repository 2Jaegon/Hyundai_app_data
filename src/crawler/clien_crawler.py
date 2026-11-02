"""
Clien '굴러간당' (cm_car) crawler using Scrapling Fetcher.
Collects community posts matching DCX search keywords.
"""
import time
import urllib.parse
from typing import List, Dict, Optional
from scrapling import Fetcher

class ClienCrawler:
    BASE_URL = "https://www.clien.net"
    
    def __init__(self, board_cd: str = "cm_car"):
        self.board_cd = board_cd
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        }

    def search_posts(self, query: str, max_pages: int = 1) -> List[Dict]:
        """Search posts within cm_car board for a specific query."""
        results = []
        encoded_q = urllib.parse.quote(query)
        
        for p in range(max_pages):
            search_url = f"{self.BASE_URL}/service/search?q={encoded_q}&boardCd={self.board_cd}&sort=recency&p={p}"
            try:
                response = Fetcher.get(search_url, headers=self.headers)
                if response.status != 200:
                    continue
                
                items = response.css(".list_item")
                for item in items:
                    subj_node = item.css(".subject_fixed")
                    if not subj_node:
                        continue
                    title = subj_node[0].get_all_text().strip()
                    
                    link_node = item.css("a")
                    link = link_node[0].attrib.get("href", "") if link_node else ""
                    if not link.startswith("http"):
                        link = urllib.parse.urljoin(self.BASE_URL, link)
                        
                    time_node = item.css(".timestamp")
                    post_date = time_node[0].get_all_text().strip() if time_node else ""
                    
                    results.append({
                        "source": "클리앙_굴러간당",
                        "query": query,
                        "title": title,
                        "url": link,
                        "date": post_date
                    })
                time.sleep(0.5)
            except Exception as e:
                print(f"[Clien] Error searching {query} page {p}: {e}")
        return results

    def fetch_post_detail(self, post_info: Dict) -> Dict:
        """Fetch article body and top comments."""
        url = post_info["url"]
        try:
            resp = Fetcher.get(url, headers=self.headers)
            if resp.status == 200:
                body_node = resp.css(".post_article")
                content = body_node[0].get_all_text().strip() if body_node else ""
                
                comment_nodes = resp.css(".comment_content")
                comments = [c.get_all_text().strip() for c in comment_nodes if c.get_all_text().strip()]
                
                post_info["content"] = content
                post_info["comments"] = comments[:10]  # Store top 10 comments
                return post_info
        except Exception as e:
            print(f"[Clien] Error fetching detail for {url}: {e}")
        
        post_info["content"] = ""
        post_info["comments"] = []
        return post_info

