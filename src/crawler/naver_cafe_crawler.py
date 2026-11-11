"""
Naver Cafe crawler using Scrapling StealthyFetcher.
Extracts posts from target Naver Cafes (motorsjason, hmcclub, etc.) via site-specific search.
"""
import time
import re
import urllib.parse
from typing import List, Dict
from scrapling import StealthyFetcher

class NaverCafeCrawler:
    BASE_SEARCH_URL = "https://search.naver.com/search.naver?where=article"
    
    def __init__(self, cafe_url: str, cafe_name: str):
        self.cafe_url = cafe_url
        self.cafe_name = cafe_name

    def search_posts(self, query: str, max_results: int = 15) -> List[Dict]:
        """Search articles belonging to this cafe with the given query."""
        search_query = f"site:cafe.naver.com/{self.cafe_url} {query}"
        encoded_q = urllib.parse.quote(search_query)
        url = f"{self.BASE_SEARCH_URL}&query={encoded_q}"
        
        results = []
        try:
            resp = StealthyFetcher.fetch(url, headless=True)
            if resp.status != 200:
                return []
            
            all_a = resp.css("a")
            grouped = {}
            for a in all_a:
                href = a.attrib.get("href", "")
                m = re.search(rf"{self.cafe_url}/(\d+)", href)
                if m:
                    aid = m.group(1)
                    if aid not in grouped:
                        clean_href = href.split("?")[0]
                        grouped[aid] = {"texts": [], "href": clean_href, "raw_href": href}
                    txt = a.get_all_text().strip().replace("새 창 열림", "").strip()
                    if txt and txt not in grouped[aid]["texts"]:
                        grouped[aid]["texts"].append(txt)
            
            for aid, data in list(grouped.items())[:max_results]:
                texts = data["texts"]
                if not texts:
                    continue
                
                # Deduce date, title, content
                date_val = ""
                title_val = ""
                content_val = ""
                
                # Check for date pattern (e.g. 2026.02.17., 3주 전, 어제, etc.)
                for t in texts:
                    if re.search(r"(\d{4}\.\d{2}\.\d{2}|시간 전|일 전|주 전|개월 전|어제|방금)", t) and len(t) < 25:
                        date_val = t
                        break
                
                remaining = [t for t in texts if t != date_val]
                if remaining:
                    title_val = remaining[0].replace("\n", " ")
                if len(remaining) > 1:
                    content_val = "\n".join(remaining[1:]).replace("\n", " ")
                else:
                    content_val = title_val

                results.append({
                    "source": f"네이버카페_{self.cafe_name}",
                    "cafe_url": self.cafe_url,
                    "query": query,
                    "article_id": aid,
                    "title": title_val,
                    "date": date_val,
                    "content": content_val,
                    "comments": [],
                    "url": data["href"]
                })
            time.sleep(1.0)
        except Exception as e:
            print(f"[NaverCafe {self.cafe_name}] Error scraping query '{query}': {e}")
            
        return results

