"""
DCX (Digital Customer Experience) Pain Point Analyzer for Hyundai App Contest (Travel Docent).
Extracts user PainPoints, customer VOCs, and feature mapping from community posts.
"""
import re
from typing import List, Dict, Any

FEATURE_RULES = {
    "Feature 1: 목적지 설정 & 드라이브 코스": {
        "keywords": ["드라이브 코스", "국도", "경치", "명소", "여행지", "코스 추천", "와인딩", "로드트립"],
        "painpoint_pattern": r"(코스|갈만한 곳|어디|추천|경치|풍경|길이|막히)",
        "docent_value": "단순 최단 경로가 아닌 풍경/테마 중심 목적지 및 숨은 드라이브 코스 가이드"
    },
    "Feature 2-1: 경유지(휴게소) 스탬프 & 빌트인캠 사진 기록": {
        "keywords": ["휴게소", "맛집", "빌트인캠", "사진", "기록", "스탬프", "경유", "추억", "블랙박스", "충전소 휴게소"],
        "painpoint_pattern": r"(휴게소|빌트인캠|사진|백업|옮기|귀찮|맛없|비싸|찾기|기록)",
        "docent_value": "휴게소 정차 시 자동 스탬프 및 차량 빌트인캠 연계 여행 풍경 사진 자동 앨범화"
    },
    "Feature 2-2: 졸음방지 대화 모드 & 인카 도슨트": {
        "keywords": ["졸음", "졸려", "피로", "지루", "심심", "아이들", "동승자", "대화", "노래", "오디오", "음악", "라디오"],
        "painpoint_pattern": r"(졸음|졸려|눈이|피곤|지루|보채|짜증|잠깨|심심)",
        "docent_value": "주행 중 AI 인터랙티브 대화, 퀴즈, 지역 랜드마크 도슨트 오디오로 졸음과 지루함 퇴치"
    },
    "Feature 2-3: 운전 매너 점수 기반 마일리지": {
        "keywords": ["운전점수", "안전운전", "급가속", "급감속", "깜빡이", "방향지시등", "우회전", "매너", "점수", "티맵"],
        "painpoint_pattern": r"(점수|급가속|급제동|깜빡이|양보|스트레스|감점|보험)",
        "docent_value": "주행 매너(급가감속 제어, 횡단보도 일시정지, 방향지시등)를 점수화하여 즉각적 마일리지 보상"
    },
    "Feature 3 & 4: 도착 마일리지 & 블루멤버스 포인트 연계": {
        "keywords": ["블루멤버스", "포인트", "마일리지", "사용처", "혜택", "할인", "적립", "환급", "현대카드", "기프트콘"],
        "painpoint_pattern": r"(블루멤버스|포인트|쓸데|사용처|소멸|아깝|혜택)",
        "docent_value": "여행 완주 및 안전운전으로 모은 마일리지를 블루멤버스 및 제휴 포인트샵에서 실사용"
    }
}

class DCXAnalyzer:
    @staticmethod
    def analyze_posts(posts: List[Dict]) -> Dict[str, Any]:
        categorized_results = {feat: [] for feat in FEATURE_RULES}
        all_voc_snippets = []
        
        for post in posts:
            title = post.get("title", "")
            content = post.get("content", "")
            comments = " ".join(post.get("comments", []))
            full_text = f"{title} {content} {comments}".strip()
            
            matched_features = []
            for feat, rule in FEATURE_RULES.items():
                kw_matches = [k for k in rule["keywords"] if k in full_text]
                has_painpoint = bool(re.search(rule["painpoint_pattern"], full_text))
                
                if kw_matches or has_painpoint:
                    intensity = "HIGH" if (len(kw_matches) >= 2 or has_painpoint) else "MEDIUM"
                    # Extract representative snippet
                    snippet = title if len(title) > 10 else content[:120]
                    categorized_results[feat].append({
                        "post_title": title,
                        "source": post.get("source", ""),
                        "url": post.get("url", ""),
                        "date": post.get("date", ""),
                        "matched_keywords": kw_matches,
                        "intensity": intensity,
                        "snippet": snippet
                    })
                    matched_features.append(feat)

        # Generate summary stats
        stats = {feat: len(items) for feat, items in categorized_results.items()}
        
        return {
            "total_posts_analyzed": len(posts),
            "feature_distribution": stats,
            "painpoints_by_feature": categorized_results
        }

    @staticmethod
    def generate_markdown_summary(community_name: str, analysis: Dict[str, Any]) -> str:
        md = []
        md.append(f"# [{community_name}] DCX 페인포인트 분석 요약 보고서\n")
        md.append(f"- **분석 게시글 수**: 총 {analysis['total_posts_analyzed']}건\n")
        md.append("## 1. '여행 도슨트' 기능별 페인포인트 & 니즈 분포\n")
        
        for feat, count in analysis["feature_distribution"].items():
            rule = FEATURE_RULES[feat]
            md.append(f"### 📌 {feat} (발견 건수: {count}건)")
            md.append(f"- **앱 서비스 가치**: {rule['docent_value']}")
            
            samples = analysis["painpoints_by_feature"][feat][:3]
            if samples:
                md.append("- **대표 고객 VOC (Voice of Customer)**:")
                for s in samples:
                    md.append(f"  - *\"{s['snippet']}\"* ([원문 링크]({s['url']}) | 강도: `{s['intensity']}`)")
            else:
                md.append("- *해당 커뮤니티 내 직접적 언급 표본 소량*")
            md.append("")
            
        return "\n".join(md)

