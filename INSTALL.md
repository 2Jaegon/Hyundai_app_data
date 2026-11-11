# 설치 가이드: Claude Code · agy(Antigravity CLI) 스킬 환경 + 수집기

다른 PC에서 이 프로젝트를 받은 뒤, 지금 PC와 같은 스킬 환경을 만드는 순서다.
**2026-11-03 Windows 11 / PowerShell 기준.** 각 명령에 이 PC에서 실제로 실행해 확인한 것(✅)과 문법만 알고 실행은 안 한 것(⚠️)을 표시했다. 새 PC에서 처음부터 끝까지 돌려 본 적은 없다.

## 0. 설치되는 것 한눈에 보기
| 스킬 | 출처 | 버전(확인 시점) | Claude Code | agy |
|---|---|---|---|---|
| ECC | `affaan-m/ECC` (플러그인 `ecc@ecc`) | 2.2.3 | 플러그인 | `agy plugin install` (스킬 321·규칙 23·MCP 1·훅 1) |
| superpowers | `obra/superpowers-marketplace` | 7.0.1 | 플러그인 | `agy plugin install` (스킬 15) |
| karpathy | `forrestchang/andrej-karpathy-skills` | 1.0.0 | 플러그인 | `agy plugin install` (스킬 1) |
| ponytail | `DietrichGebert/ponytail` | 5.3.3 | 플러그인 | `agy plugin install` (스킬 6) |
| find-skills | `vercel-labs/skills` (skills/find-skills) | 2026-10-31 설치본 | `~/.claude/skills` | `~/.gemini/config/skills`로 복사 |
| graphify | PyPI `graphifyy` (`Graphify-Labs/graphify`) | 0.9.100 | `graphify install --platform claude` | `graphify install --platform antigravity-windows` |

프로젝트 안에 같이 들어 있는 것: `.agents/skills/vercel-react-best-practices`(프로젝트 스킬), `dcx_collector/GEMINI.md`(Gemini/agy용 수집 지시서).

## 1. 사전 준비
- Claude Code CLI(`claude`), agy(`%LOCALAPPDATA%\agy\bin\agy.exe`, v1.4.12 확인), Python ≥ 3.10(이 PC는 3.14), Node ≥ 22.20(`npx skills` 용). agy 설치 방법은 Antigravity 공식 안내를 따른다(이 문서에서 확인하지 않음).
- pip가 `--user`로 설치하면 `...\Python\Python314\Scripts`가 PATH에 없을 수 있다. 아래 명령이 "찾을 수 없음"이면 그 폴더를 PATH에 추가하거나 전체 경로로 실행한다.

## 2. Claude Code 스킬
```powershell
# 마켓플레이스 등록 (⚠️ 문법 미실행. 이 PC에는 이미 등록돼 있음)
claude plugin marketplace add affaan-m/ECC
claude plugin marketplace add obra/superpowers-marketplace
claude plugin marketplace add forrestchang/andrej-karpathy-skills
claude plugin marketplace add DietrichGebert/ponytail

# 플러그인 설치 (⚠️ 문법 미실행)
claude plugin install ecc@ecc
claude plugin install superpowers@superpowers-marketplace
claude plugin install andrej-karpathy-skills@karpathy-skills
claude plugin install ponytail@ponytail

# find-skills (⚠️ 이 PC는 skills CLI로 설치됨. 출처: vercel-labs/skills)
npx skills add vercel-labs/skills@find-skills -g -y

# 프로젝트 스킬 (✅ 이 PC에서 실행). git에 .agents/ 를 올렸다면 생략 가능
npx skills add vercel-labs/agent-skills@vercel-react-best-practices -y
```
ECC 훅 강도 설정(✅ 이 PC에서 `standard`로 실행, `~/.claude/settings.json`에 기록됨):
```powershell
$root = (claude plugin list --json | ConvertFrom-Json | Where-Object id -eq "ecc@ecc").installPath
node "$root\scripts\setup.js" --mode claude-plugin --scope user --hooks standard --yes --json
```
설치 후 Claude Code에서 `/reload-plugins`를 실행하거나 재시작한다.

## 3. agy 스킬
`agy`는 Claude Code 플러그인을 `plugin install <폴더>`로 변환해 설치한다(✅ 4개 모두 이 방식으로 설치). **2번을 먼저 끝낸 뒤** 실행한다.
```powershell
$ids = "ecc@ecc","superpowers@superpowers-marketplace","andrej-karpathy-skills@karpathy-skills","ponytail@ponytail"
claude plugin list --json | ConvertFrom-Json | Where-Object { $ids -contains $_.id } |
  ForEach-Object { agy plugin install $_.installPath }
agy plugin list      # ecc, superpowers, andrej-karpathy-skills, ponytail 이 보여야 함
```
find-skills 와 graphify 는 agy 가 `~/.gemini/config/skills` 에서 읽는다(`~/.agents/skills`는 읽지 않음, ✅ 확인).
```powershell
# find-skills: 위 2번에서 설치된 폴더를 복사 (✅)
Copy-Item "$env:USERPROFILE\.agents\skills\find-skills" "$env:USERPROFILE\.gemini\config\skills\find-skills" -Recurse

# graphify (✅ Windows)
pip install graphifyy
$g = "$env:APPDATA\Python\Python314\Scripts\graphify.exe"   # PATH에 있으면 graphify
& $g install --platform claude                # ~/.claude/skills/graphify + ~/.claude/CLAUDE.md(3줄) 생성
& $g install --platform antigravity-windows   # ~/.gemini/config/skills/graphify
```
macOS/Linux의 agy 플랫폼 이름은 확인하지 않았다(`graphify install --help`의 목록 참고).

## 4. 설치 확인
```powershell
claude plugin list           # 4개 플러그인 enabled
agy plugin list
# agy 가 6개 스킬을 모두 인식하는지 (빈 폴더에서 실행)
mkdir $env:TEMP\agychk -Force | Out-Null; cd $env:TEMP\agychk
agy -p "스킬 목록에 find-skills, graphify, ponytail, brainstorming, karpathy-guidelines, tdd-workflow 가 각각 있는지 'name: yes/no' 한 줄씩만 답해" --output-format json
```
이 PC 결과: 전부 yes.

## 5. 수집기(`dcx_collector`)
```powershell
pip install "scrapling[fetchers]"      # ✅ v0.5
scrapling install                       # 브라우저 바이너리 (✅. StealthyFetcher 등은 쓰지 않음)
cd dcx_collector
$env:PYTHONUTF8 = 1
python collect.py --selftest
python collect.py --source bobaedream --max-pages 6 --per-query 40 --max-posts 3000
python collect.py --source ppomppu    --max-pages 6 --per-query 40 --max-posts 3000
python classify.py --all                # agy 로 관련성 판정 (agy 필요)
python classify.py --export; python collect.py --export   # data/*.csv
```
규칙과 허용·금지 소스는 `dcx_collector/GEMINI.md` 참고. 네이버 카페·클리앙 검색 등은 `robots.txt` 금지라 수집하지 않는다.

## 6. git 업로드 전 주의
- `dcx_collector/data/` 는 외부 사이트 글을 수집한 데이터다(작성자 미저장, 전화·이메일 마스킹). 사이트 이용약관은 읽지 않았으므로 **공개 저장소에는 올리지 않는 것을 권장**한다. `.gitignore`에 `dcx_collector/data/` 추가.
- ECC 는 훅과 MCP 서버(`npx chrome-devtools-mcp`)를 포함한다. 새 PC에서 설치 전 내용을 확인한다.
- `~/.claude/settings.json`, `~/.gemini/` 같은 개인 설정은 저장소에 포함되지 않는다. 이 문서의 명령으로 다시 만든다.
- 버전은 위 표 기준이다. 플러그인이 업데이트되면 스킬 수와 동작이 달라질 수 있다.
