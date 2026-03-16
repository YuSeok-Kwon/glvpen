"""
2026 WBC 한국 대표팀 분석 - DB 없이 로스터 기반 단독 실행.

로컬에 DB 없을 때도 로스터 구조 분석 + 전력 평가 가능.
DB 접속 가능 시 main.py --wbc로 전체 분석 실행.
"""
import json
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from wbc.roster import WBC_ROSTER, ALL_PLAYERS, PITCHER_NAMES, BATTER_NAMES, get_team_distribution


def analyze_roster():
    """로스터 구조 분석 (DB 불필요)"""

    print("=" * 60)
    print("  2026 WBC 한국 대표팀 로스터 분석")
    print("=" * 60)

    # 1. 전체 구성
    total = len(ALL_PLAYERS)
    n_pitchers = len(PITCHER_NAMES)
    n_batters = len(BATTER_NAMES)
    print(f"\n[1] 전체 구성: {total}명 (투수 {n_pitchers}명 / 야수 {n_batters}명)")
    print(f"    투수:야수 비율 = {n_pitchers/total*100:.1f}% : {n_batters/total*100:.1f}%")

    # 2. 포지션별 상세
    print(f"\n[2] 포지션별 구성")
    for pos, players in WBC_ROSTER.items():
        print(f"    {pos} ({len(players)}명)")
        for p in players:
            role = f" [{p['role']}]" if 'role' in p else ""
            print(f"      - {p['name']} ({p['team']}){role}")

    # 3. 팀별 선발 분포
    team_dist = get_team_distribution()
    print(f"\n[3] 팀별 선발 인원 분포")
    for team, count in team_dist.items():
        bar = "█" * count
        print(f"    {team:5s} │ {bar} {count}명")

    # 4. 투수진 분석
    starters = [p for p in WBC_ROSTER['투수'] if p['role'] == '선발']
    relievers = [p for p in WBC_ROSTER['투수'] if p['role'] == '불펜']
    print(f"\n[4] 투수진 구성")
    print(f"    선발: {len(starters)}명 - {', '.join(p['name'] for p in starters)}")
    print(f"    불펜: {len(relievers)}명 - {', '.join(p['name'] for p in relievers)}")
    print(f"    선발:불펜 = {len(starters)}:{len(relievers)}")

    # 5. 팀별 기여도 분석
    print(f"\n[5] 팀별 기여도 분석")
    team_positions = {}
    for pos, players in WBC_ROSTER.items():
        for p in players:
            team = p['team']
            if team not in team_positions:
                team_positions[team] = {}
            if pos not in team_positions[team]:
                team_positions[team][pos] = []
            team_positions[team][pos].append(p['name'])

    for team in sorted(team_positions.keys(), key=lambda t: team_dist.get(t, 0), reverse=True):
        positions = team_positions[team]
        pos_summary = []
        for pos, names in positions.items():
            pos_summary.append(f"{pos}: {', '.join(names)}")
        print(f"    {team} ({team_dist[team]}명)")
        for ps in pos_summary:
            print(f"      {ps}")

    # 6. 핵심 포인트 요약
    print(f"\n[6] 전력 평가 포인트")

    # LG 비중
    lg_count = team_dist.get('LG', 0)
    lg_pct = lg_count / total * 100
    print(f"    - LG 트윈스 최다 선발 {lg_count}명 ({lg_pct:.0f}%): 팀 케미스트리 활용 가능")

    # KT 투수진
    kt_pitchers = [p['name'] for p in WBC_ROSTER['투수'] if p['team'] == 'KT']
    if kt_pitchers:
        print(f"    - KT 투수 {len(kt_pitchers)}명 ({', '.join(kt_pitchers)}): 투수진 핵심 축")

    # 핵심 타자
    key_batters_info = {
        '김도영': 'KIA 유격수, 2025 MVP 후보급 - 타격+주루+수비 올라운드',
        '노시환': '한화 1루수, 장거리 포 - 국제대회 클린업 기대',
        '구자욱': '삼성 외야수, 안정적 타격 + 국제대회 경험 풍부',
    }
    print(f"    - 핵심 타자:")
    for name, desc in key_batters_info.items():
        print(f"      {name}: {desc}")

    # 경험치
    experienced = ['류현진', '박해민', '구자욱', '박동원']
    exp_in_roster = [n for n in experienced if n in ALL_PLAYERS]
    print(f"    - 국제대회 경험자: {', '.join(exp_in_roster)} ({len(exp_in_roster)}명)")

    # 젊은 전력
    young_guns = ['김도영', '문현빈', '안현민', '손주영', '송승기']
    young_in_roster = [n for n in young_guns if n in ALL_PLAYERS]
    print(f"    - 신예 전력: {', '.join(young_in_roster)} ({len(young_in_roster)}명)")

    # 7. 약점/보완점
    print(f"\n[7] 약점 및 보완점")
    print(f"    - 포수진: 2명 구성 (박동원, 김형준) - 부상 시 대체 인력 부족")
    print(f"    - 선발 투수: 4명 (고영표, 조병현, 곽빈, 류현진) - 로테이션 여유 있음")
    print(f"    - 불펜: 9명 - 숏스타터 전략 병행 가능")
    print(f"    - 좌타 비중 확인 필요 - 좌우 밸런스 점검 사항")

    # 8. 대진 관련 (Pool C - 일본, 도쿄)
    print(f"\n[8] 대회 정보 (Pool C)")
    print(f"    - 일정: 2026.03.05 ~ 03.10")
    print(f"    - 장소: 오사카 돔 (일본)")
    print(f"    - 같은 조: 일본 + 기타 팀")
    print(f"    - 특이사항: 오릭스/한신 1군과 연습경기 예정")

    print(f"\n{'='*60}")
    print(f"  분석 완료 - DB 연동 시 main.py --wbc로 전체 통계 분석 가능")
    print(f"{'='*60}")

    return team_dist, team_positions


if __name__ == '__main__':
    analyze_roster()
