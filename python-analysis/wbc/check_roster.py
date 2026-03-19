"""
WBC 로스터 선수 DB 매칭 진단.
어떤 선수가 DB에서 찾아지고, 어떤 선수가 누락되는지 확인.
"""
import sys, os, warnings
warnings.filterwarnings("ignore")
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.db_connector import DBConnector
from wbc.roster import WBC_ROSTER, PITCHER_NAMES, BATTER_NAMES, ALL_PLAYERS

db = DBConnector()

# 2025 시즌 전체 타자/투수 조회
batters = db.get_batters(2025)
pitchers = db.get_pitchers(2025)

print("=" * 60)
print("  WBC 로스터 DB 매칭 진단")
print("=" * 60)

# 투수 매칭
print(f"\n[투수] 로스터: {len(PITCHER_NAMES)}명")
pit_names_in_db = set(pitchers['playerName'].tolist()) if not pitchers.empty else set()
for p in WBC_ROSTER['투수']:
    name = p['name']
    team = p['team']
    found = name in pit_names_in_db
    mark = "O" if found else "X"
    print(f"  [{mark}] {name} ({team}) {'' if found else '← DB에 없음'}")

# 야수 매칭
print(f"\n[야수] 로스터: {len(BATTER_NAMES)}명")
bat_names_in_db = set(batters['playerName'].tolist()) if not batters.empty else set()
for pos in ['포수', '내야수', '외야수']:
    for p in WBC_ROSTER[pos]:
        name = p['name']
        team = p['team']
        found = name in bat_names_in_db
        mark = "O" if found else "X"
        print(f"  [{mark}] {name} ({team}) - {pos} {'' if found else '← DB에 없음'}")

# 유사 이름 검색 (누락된 선수)
missing = [n for n in ALL_PLAYERS if n not in pit_names_in_db and n not in bat_names_in_db]
if missing:
    print(f"\n[누락 선수 유사 이름 검색]")
    all_db_names = list(pit_names_in_db | bat_names_in_db)
    for name in missing:
        # 성이 같은 선수 찾기
        similar = [n for n in all_db_names if n[0] == name[0]]
        if similar:
            print(f"  {name} → 유사: {', '.join(similar[:5])}")
        else:
            print(f"  {name} → 유사 이름 없음")

# 투수인데 타자 테이블에 있는 경우 확인
print(f"\n[투수가 타자 테이블에 있는지 확인]")
for name in PITCHER_NAMES:
    if name not in pit_names_in_db and name in bat_names_in_db:
        print(f"  {name}: 투수 테이블 X, 타자 테이블 O")

print(f"\n[요약]")
found_pit = sum(1 for n in PITCHER_NAMES if n in pit_names_in_db)
found_bat = sum(1 for n in BATTER_NAMES if n in bat_names_in_db)
print(f"  투수 매칭: {found_pit}/{len(PITCHER_NAMES)}명")
print(f"  야수 매칭: {found_bat}/{len(BATTER_NAMES)}명")
print(f"  총 매칭: {found_pit + found_bat}/{len(ALL_PLAYERS)}명")

db.close()
