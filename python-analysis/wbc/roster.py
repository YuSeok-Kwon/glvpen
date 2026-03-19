"""
2026 WBC 한국 대표팀 로스터 정보.
사용자 제공 명단 기반.
"""

# 2026 WBC 한국 대표팀 로스터 (포지션별)
WBC_ROSTER = {
    '투수': [
        {'name': '고영표', 'team': 'KT', 'role': '선발'},
        {'name': '조병현', 'team': 'SSG', 'role': '선발'},
        {'name': '곽빈', 'team': '두산', 'role': '선발'},
        {'name': '박영현', 'team': 'KT', 'role': '불펜'},
        {'name': '소형준', 'team': 'KT', 'role': '불펜'},
        {'name': '김택연', 'team': '두산', 'role': '불펜'},
        {'name': '노경은', 'team': 'SSG', 'role': '불펜'},
        {'name': '유영찬', 'team': 'LG', 'role': '불펜'},
        {'name': '정우주', 'team': '한화', 'role': '불펜'},
        {'name': '김영규', 'team': 'NC', 'role': '불펜'},
        {'name': '류현진', 'team': '한화', 'role': '선발'},
        {'name': '손주영', 'team': 'LG', 'role': '불펜'},
        {'name': '송승기', 'team': 'LG', 'role': '불펜'},
    ],
    '포수': [
        {'name': '박동원', 'team': 'LG'},
        {'name': '김형준', 'team': 'NC'},
    ],
    '내야수': [
        {'name': '김도영', 'team': 'KIA'},
        {'name': '김주원', 'team': 'NC'},
        {'name': '노시환', 'team': '한화'},
        {'name': '문보경', 'team': 'LG'},
        {'name': '신민재', 'team': 'LG'},
    ],
    '외야수': [
        {'name': '구자욱', 'team': '삼성'},
        {'name': '문현빈', 'team': '한화'},
        {'name': '박해민', 'team': 'LG'},
        {'name': '안현민', 'team': 'KT'},
    ],
}

# 전체 선수명 리스트
ALL_PLAYERS = []
for position_group in WBC_ROSTER.values():
    for p in position_group:
        ALL_PLAYERS.append(p['name'])

# 투수 선수명 리스트
PITCHER_NAMES = [p['name'] for p in WBC_ROSTER['투수']]

# 야수(포수+내야+외야) 선수명 리스트
BATTER_NAMES = []
for pos in ['포수', '내야수', '외야수']:
    for p in WBC_ROSTER[pos]:
        BATTER_NAMES.append(p['name'])

# 팀별 선발 인원수
def get_team_distribution():
    """팀별 대표팀 선발 인원수"""
    dist = {}
    for pos_group in WBC_ROSTER.values():
        for p in pos_group:
            team = p['team']
            dist[team] = dist.get(team, 0) + 1
    return dict(sorted(dist.items(), key=lambda x: x[1], reverse=True))
