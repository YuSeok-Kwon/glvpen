"""
WBC 분석 결과 기반 컬럼 기사 자동 생성.
DB에 저장된 분석 결과를 읽어 마크다운 형식 기사를 출력.

실행: python wbc/write_column.py
"""
import json
import sys
import os
import warnings
warnings.filterwarnings("ignore")

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.db_connector import DBConnector


def generate_column():
    db = DBConnector()

    # 분석 결과 조회
    df = db.query("SELECT * FROM analysis_result WHERE topic = 20")
    if df.empty:
        print("WBC 분석 결과가 없습니다. 먼저 'python main.py --wbc'를 실행하세요.")
        db.close()
        return

    row = df.iloc[0]
    stats = json.loads(row["stats_json"])
    hypothesis = json.loads(row["hypothesis_results"])
    insight = row["insight_text"]
    charts = json.loads(row["chart_json"])

    # ==================== 기사 생성 ====================
    print("=" * 70)
    print()

    # 제목
    print("# 2026 WBC 한국 대표팀 전력 분석: 데이터로 본 태극마크의 경쟁력")
    print()
    print("*BaseBall LOCK 3.0 데이터 분석팀 | 2026.03.05*")
    print()

    # 리드
    batter_count = len(stats.get("대표팀_타자_성적", []))
    pitcher_count = len(stats.get("대표팀_투수_성적", []))
    print(f"2026 WBC가 오늘(3월 5일) 개막한다. 한국 대표팀은 투수 {pitcher_count}명, "
          f"야수 {batter_count}명 총 {pitcher_count + batter_count}명으로 구성되어 "
          f"일본 오사카에서 Pool C 경기에 돌입한다. "
          f"KBO {row['season']} 시즌 데이터를 기반으로 대표팀의 전력을 분석했다.")
    print()

    # ==================== 타자 분석 ====================
    print("## 1. 타선 전력: KBO를 대표하는 타격 라인업")
    print()

    bat_stats = stats.get("대표팀_타자_성적", [])
    if bat_stats:
        print("### 대표팀 타자 성적표 (2025 KBO 정규시즌)")
        print()
        print("| 선수 | 소속 | 포지션 | WAR | AVG | OPS | HR |")
        print("|------|------|--------|-----|-----|-----|----|")
        for b in bat_stats:
            name = b.get("playerName", "")
            team = b.get("teamName", "")
            pos = b.get("position", "")
            war = b.get("WAR", 0)
            avg = b.get("AVG", 0)
            ops = b.get("OPS", 0)
            hr = b.get("HR", 0)
            print(f"| {name} | {team} | {pos} | {war:.1f} | {avg:.3f} | {ops:.3f} | {int(hr)} |")
        print()

    # 타자 기술통계
    for metric in ["WAR", "AVG", "OPS"]:
        key = f"대표팀_타자_{metric}"
        if key in stats and isinstance(stats[key], dict):
            s = stats[key]
            print(f"- 대표팀 타자 {metric}: "
                  f"평균 {s['mean']:.3f}, 중앙값 {s['median']:.3f}, "
                  f"표준편차 {s['std']:.3f} (n={s['n']})")

    # 가설검정: 대표팀 vs KBO
    print()
    for metric in ["AVG", "OPS", "WAR"]:
        key = f"대표팀vs리그_타자_{metric}"
        if key in hypothesis:
            h = hypothesis[key]
            sig = "유의미한 차이" if h.get("significant") else "통계적 차이 없음"
            print(f"**가설검정**: 대표팀 타자 {metric} vs KBO 리그 평균 → "
                  f"{sig} (p={h.get('p_value', 'N/A'):.4f}), "
                  f"대표팀 {h.get('group1_mean', 0):.3f} vs KBO {h.get('group2_mean', 0):.3f}")

    print()

    # ==================== 투수 분석 ====================
    print("## 2. 투수진 전력: 선발-불펜 구성 분석")
    print()

    pit_stats = stats.get("대표팀_투수_성적", [])
    if pit_stats:
        print("### 대표팀 투수 성적표 (2025 KBO 정규시즌)")
        print()
        print("| 선수 | 소속 | WAR | ERA | WHIP | K/9 | IP |")
        print("|------|------|-----|-----|------|-----|----|")
        for p in pit_stats:
            name = p.get("playerName", "")
            team = p.get("teamName", "")
            war = p.get("WAR", 0)
            era = p.get("ERA", 0)
            whip = p.get("WHIP", 0)
            k9 = p.get("K/9", 0)
            ip = p.get("IP", 0)
            print(f"| {name} | {team} | {war:.1f} | {era:.2f} | {whip:.2f} | {k9:.1f} | {ip:.0f} |")
        print()

    # 선발 vs 불펜
    if "선발_ERA" in stats and "불펜_ERA" in stats:
        s_era = stats["선발_ERA"]
        b_era = stats["불펜_ERA"]
        print(f"- 선발 평균 ERA: {s_era['mean']:.2f} ({s_era['n']}명)")
        print(f"- 불펜 평균 ERA: {b_era['mean']:.2f} ({b_era['n']}명)")

    if "선발vs불펜_ERA" in hypothesis:
        h = hypothesis["선발vs불펜_ERA"]
        print(f"\n**가설검정**: 선발 vs 불펜 ERA 차이 → "
              f"{h.get('interpretation', '')} (p={h.get('p_value', 'N/A'):.4f})")

    # 투수 가설검정
    for metric in ["ERA", "K/9", "WAR"]:
        key = f"대표팀vs리그_투수_{metric}"
        if key in hypothesis:
            h = hypothesis[key]
            print(f"\n**가설검정**: 대표팀 투수 {metric} vs KBO 리그 평균 → "
                  f"{h.get('interpretation', '')} (p={h.get('p_value', 'N/A'):.4f})")

    print()

    # ==================== 팀별 분포 ====================
    print("## 3. 팀별 선발 분포: LG 중심의 대표팀")
    print()
    team_dist = stats.get("팀별_선발_분포", {})
    if team_dist:
        total = sum(team_dist.values())
        for team, count in team_dist.items():
            pct = count / total * 100
            bar = "█" * count
            print(f"  {team:5s} │ {bar} {count}명 ({pct:.0f}%)")
        print()
        top_team = list(team_dist.keys())[0]
        top_count = list(team_dist.values())[0]
        print(f"{top_team}가 {top_count}명으로 최다 선발되었다. "
              f"같은 팀 선수가 많으면 호흡과 커뮤니케이션에서 이점이 있지만, "
              f"특정 팀 편중은 전력 다양성 측면에서 리스크가 될 수 있다.")
    print()

    # ==================== 성장 추이 ====================
    print("## 4. 핵심 타자 성장 추이")
    print()
    for name in ["김도영", "노시환", "구자욱"]:
        key = f"{name}_성장추이"
        if key in stats:
            data = stats[key]
            seasons = data.get("seasons", [])
            wars = data.get("WAR", [])
            if seasons and wars:
                trend = " → ".join([f"{s}:{w}" for s, w in zip(seasons, wars)])
                direction = "상승세" if len(wars) >= 2 and wars[-1] > wars[0] else "하락세" if len(wars) >= 2 and wars[-1] < wars[0] else "유지"
                print(f"- **{name}** WAR 추이: {trend} ({direction})")
    print()

    # ==================== 차트 목록 ====================
    print("## 5. 시각화 차트 목록")
    print()
    for i, c in enumerate(charts, 1):
        print(f"  {i}. [{c.get('chartType', '?')}] {c.get('title', '제목 없음')}")
    print()

    # ==================== 총평 ====================
    print("## 총평")
    print()

    # 강점/약점 자동 판별
    strengths = []
    weaknesses = []

    for key, h in hypothesis.items():
        if h.get("significant"):
            if "대표팀vs리그_타자" in key:
                if h.get("group1_mean", 0) > h.get("group2_mean", 0):
                    strengths.append(f"타자 {key.split('_')[-1]}에서 KBO 평균 대비 유의미하게 높음")
                else:
                    weaknesses.append(f"타자 {key.split('_')[-1]}에서 KBO 평균 대비 유의미하게 낮음")
            elif "대표팀vs리그_투수" in key:
                metric_name = key.split("_")[-1]
                if metric_name == "ERA":
                    if h.get("group1_mean", 0) < h.get("group2_mean", 0):
                        strengths.append("투수 ERA가 KBO 평균보다 유의미하게 낮음")
                    else:
                        weaknesses.append("투수 ERA가 KBO 평균보다 유의미하게 높음")
                elif metric_name in ["WAR", "K/9"]:
                    if h.get("group1_mean", 0) > h.get("group2_mean", 0):
                        strengths.append(f"투수 {metric_name}에서 KBO 평균 대비 유의미하게 높음")

    if strengths:
        print("**강점**:")
        for s in strengths:
            print(f"  - {s}")
    if weaknesses:
        print("**약점**:")
        for w in weaknesses:
            print(f"  - {w}")

    if not strengths and not weaknesses:
        print("통계적으로 유의미한 차이가 발견된 항목이 없어, "
              "대표팀 전력이 KBO 리그 상위권 선수들의 평균적인 수준에 부합한다고 해석할 수 있다.")

    print()
    print("Pool C에서 일본과 직접 맞대결이 예정되어 있는 만큼, "
          "투수진의 이닝 관리와 핵심 타자의 클러치 상황 대응이 관건이 될 것이다.")

    print()
    print("---")
    print("*본 분석은 BaseBall LOCK 3.0 Python 배치 분석 시스템으로 자동 생성되었습니다.*")
    print("*통계 검정: scipy (Welch t-test), 유의수준 α=0.05*")
    print()
    print("=" * 70)

    db.close()


if __name__ == "__main__":
    generate_column()
