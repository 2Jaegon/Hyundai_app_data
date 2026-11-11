from google_play_scraper import search
keywords = ['오디', '스탬프투어', '티맵', '카카오내비', '마이클', '차봇', '위드라이브', 'Autio']
for kw in keywords:
    try:
        res = search(kw, lang='ko', country='kr')
        if res:
            top = res[0]
            print(f"{kw}: {top.get('title')} | {top.get('appId')}")
    except Exception as e:
        print(f"{kw} failed: {e}")

