"""
경기 분석 결과 기반 컬럼 기사 생성.
g1~g7 JSON 출력을 읽어 HTML 컬럼을 생성하고 analysis_column 테이블에 저장.

실행:
  python3 game/write_columns.py                    # 전체 컬럼 생성
  python3 game/write_columns.py --analysis g1,g3   # 특정 분석만
  python3 game/write_columns.py --dry-run          # 미리보기 (DB 저장 안함)
"""
import sys
import os
import json
import argparse
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.db_connector import DBConnector
from game.game_common import LATEST_SEASON, GAME_TYPES, get_game_type_label

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')
HISTORY_PATH = os.path.join(OUTPUT_DIR, 'run_history.json')
SEASON = LATEST_SEASON

# ==================== 분석별 메타데이터 ====================

ANALYSIS_META = {
    'g1': {
        'title_template': '이닝별 득점 패턴: {teams}',
        'subtitle': '이닝 구간별 득점 분포와 전반/후반 밸런스 분석',
        'category': 'game',
        'description': '어느 이닝에 가장 많이 득점하는지, 전반과 후반의 득점 비중을 비교 분석합니다.',
    },
    'g2': {
        'title_template': '역전/추격 패턴: {teams}',
        'subtitle': '역전승 비율과 역전 발생 이닝 분석',
        'category': 'game',
        'description': '역전승, 선행승 비율을 분석하고 몇 회에 역전이 가장 많이 발생하는지 파악합니다.',
    },
    'g3': {
        'title_template': '키플레이어 분석: {teams}',
        'subtitle': 'WPA 기반 경기 MVP 분석',
        'category': 'game',
        'description': 'WPA 1위 선수가 팀 승리에 미치는 영향과 키플레이어 집중도를 분석합니다.',
    },
    'g4': {
        'title_template': '승리 요인 분석: {teams}',
        'subtitle': '안타/실책/볼넷이 승패에 미치는 영향',
        'category': 'game',
        'description': '안타 차이별 승률, 실책의 영향 등 경기 승리를 결정짓는 핵심 요인을 분석합니다.',
    },
    'g5': {
        'title_template': '경기 시간 분석: {teams}',
        'subtitle': '경기 시간과 득점/안타의 상관관계',
        'category': 'game',
        'description': '팀별 평균 경기 시간과 득점/안타 수의 관계를 분석합니다.',
    },
    'g6': {
        'title_template': '접전/대패 분류: {teams}',
        'subtitle': '점수차 기반 경기 유형 분류와 승률 분석',
        'category': 'game',
        'description': '1~2점차 접전, 5점+ 대파 경기의 비율과 유형별 승률을 분석합니다.',
    },
    'g7': {
        'title_template': '라인업 효과: {teams}',
        'subtitle': '1번 타자 유형별 팀 득점과 라인업 안정성',
        'category': 'game',
        'description': '라인업 구성이 팀 성적에 미치는 영향을 분석합니다.',
    },
}


# ==================== 콘텐츠 생성 ====================

def load_history() -> dict:
    if os.path.exists(HISTORY_PATH):
        with open(HISTORY_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}


def load_team_charts(key: str, team_name: str, game_type: str) -> list:
    """분석 결과 JSON 로드"""
    filepath = os.path.join(OUTPUT_DIR, f'{key}_{team_name}_{SEASON}_{game_type}.json')
    if os.path.exists(filepath):
        with open(filepath, 'r', encoding='utf-8') as f:
            return json.load(f)
    return []


def get_team_info_map(db, team_ids: list) -> dict:
    if not team_ids:
        return {}
    placeholders = ','.join(['%s'] * len(team_ids))
    sql = f"""
        SELECT t.id AS teamId, t.name AS teamName
        FROM team t
        WHERE t.id IN ({placeholders})
    """
    df = db.query(sql, tuple(team_ids))
    result = {}
    for _, row in df.iterrows():
        result[int(row['teamId'])] = row.to_dict()
    return result


def build_chart_summary_html(charts: list, team_name: str) -> str:
    """차트 요약 HTML 생성"""
    html_parts = []

    for chart in charts:
        chart_type = chart.get('chartType', '')
        title = chart.get('title', '')

        if chart_type == 'bar':
            datasets = chart.get('datasets', [])
            labels = chart.get('labels', [])
            for ds in datasets[:1]:
                data = ds.get('data', [])
                if labels and data:
                    pairs = [(l, d) for l, d in zip(labels, data)
                             if d is not None and isinstance(d, (int, float))]
                    if pairs:
                        top = max(pairs, key=lambda x: x[1])
                        html_parts.append(
                            f'<span style="margin-right:16px;">'
                            f'최고: <strong>{top[0]}</strong> ({top[1]})'
                            f'</span>'
                        )

        elif chart_type == 'doughnut':
            labels = chart.get('labels', [])
            datasets = chart.get('datasets', [])
            if datasets:
                data = datasets[0].get('data', [])
                total = sum(d for d in data if isinstance(d, (int, float)))
                if total > 0 and labels:
                    items = [f'{l}: {d} ({d/total*100:.0f}%)'
                             for l, d in zip(labels, data) if isinstance(d, (int, float))]
                    html_parts.append(', '.join(items))

        elif chart_type == 'scatter':
            datasets = chart.get('datasets', [])
            for ds in datasets[:1]:
                points = ds.get('data', [])
                if points:
                    html_parts.append(f'데이터 포인트 {len(points)}개')

    if html_parts:
        return (
            f'<div style="background:#fff3cd;border-radius:6px;padding:10px 14px;'
            f'margin:8px 0 16px;font-size:0.9rem;">'
            + '<br>'.join(html_parts)
            + '</div>'
        )
    return ''


def generate_column_html(key: str, history_entries: list, db, game_type: str) -> tuple:
    """컬럼 HTML 생성"""
    meta = ANALYSIS_META.get(key, {})
    team_names = [e['team_name'] for e in history_entries]
    team_ids = [e['team_id'] for e in history_entries]

    info_map = get_team_info_map(db, team_ids)

    gt_label = get_game_type_label(game_type)
    title_teams = ', '.join(team_names[:3])
    title = meta.get('title_template', '{teams} 분석').format(teams=title_teams)
    title = f"[{SEASON} {gt_label}] {title}"

    all_charts = []
    for name in team_names:
        charts = load_team_charts(key, name, game_type)
        all_charts.extend(charts)

    parts = []

    # 도입부
    subtitle = meta.get('subtitle', '')
    description = meta.get('description', '')
    parts.append(f'<p><strong>{subtitle}</strong></p>')
    parts.append(f'<p>{description}</p>')
    parts.append('<hr style="margin:1.5rem 0;">')

    # 팀별 섹션
    for i, entry in enumerate(history_entries):
        tid = entry['team_id']
        tname = entry['team_name']

        parts.append(f'<h3 style="margin-top:2rem;">{i+1}. {tname}</h3>')

        info = info_map.get(tid, {})
        if info:
            parts.append(
                f'<div style="background:#f8f9fa;border-radius:8px;padding:12px 16px;'
                f'margin-bottom:12px;border-left:4px solid #0f3460;">'
                f'<strong>{info.get("teamName", tname)}</strong>'
                f'</div>'
            )

        team_charts = load_team_charts(key, tname, game_type)
        chart_summary = build_chart_summary_html(team_charts, tname)
        if chart_summary:
            parts.append(chart_summary)

        chart_count = len(team_charts)
        if chart_count > 0:
            chart_titles = [c.get('title', '') for c in team_charts if c.get('title')]
            if chart_titles:
                chart_list = ''.join(f'<li>{t}</li>' for t in chart_titles)
                parts.append(
                    f'<details style="margin:8px 0 16px;">'
                    f'<summary style="cursor:pointer;color:#0f3460;font-weight:600;">'
                    f'차트 {chart_count}개 포함</summary>'
                    f'<ul style="margin-top:8px;color:#6c757d;font-size:0.9rem;">'
                    f'{chart_list}</ul></details>'
                )

    # 마무리
    parts.append('<hr style="margin:2rem 0 1rem;">')
    parts.append(
        f'<p style="color:#adb5bd;font-size:0.85rem;">'
        f'본 분석은 BaseBall LOCK 3.0 Python 배치 분석 시스템으로 자동 생성되었습니다.<br>'
        f'분석 일시: {datetime.now().strftime("%Y년 %m월 %d일")}</p>'
    )

    content_html = '\n'.join(parts)
    chart_json_str = json.dumps(all_charts, ensure_ascii=False) if all_charts else None
    summary = f'{SEASON} {gt_label} {title_teams}의 {meta.get("subtitle", "분석")}.'

    return title, content_html, chart_json_str, summary, team_ids


# ==================== DB 저장 ====================

def save_column(db, title, content, chart_json, summary,
                category, team_id=None, season=SEASON):
    cursor = db.conn.cursor()

    cursor.execute(
        "SELECT id FROM analysis_column WHERE title = %s AND season = %s",
        (title, season)
    )
    existing = cursor.fetchone()

    if existing:
        cursor.execute("""
            UPDATE analysis_column
            SET content = %s, chartData = %s, summary = %s,
                publishDate = NOW(), autoGenerated = TRUE
            WHERE id = %s
        """, (content, chart_json, summary, existing[0]))
        column_id = existing[0]
        action = "UPDATE"
    else:
        cursor.execute("""
            INSERT INTO analysis_column
                (title, content, category, relatedTeamId, season,
                 chartData, publishDate, viewCount, autoGenerated, summary, featured)
            VALUES (%s, %s, %s, %s, %s, %s, NOW(), 0, TRUE, %s, FALSE)
        """, (title, content, category, team_id, season, chart_json, summary))
        column_id = cursor.lastrowid
        action = "INSERT"

    db.conn.commit()
    cursor.close()
    return column_id, action


# ==================== 메인 ====================

def main():
    parser = argparse.ArgumentParser(description='경기 분석 컬럼 생성기')
    parser.add_argument('--analysis', type=str, default=None,
                        help='컬럼 생성할 분석 (쉼표 구분, 예: g1,g3)')
    parser.add_argument('--dry-run', action='store_true',
                        help='미리보기만 (DB 저장 안함)')
    args = parser.parse_args()

    history = load_history()
    if not history:
        print("  실행 이력이 없습니다. 먼저 'python3 game/run_all.py'를 실행하세요.")
        return

    if args.analysis:
        target_keys = [k.strip().lower() for k in args.analysis.split(',')]
    else:
        target_keys = sorted(ANALYSIS_META.keys())

    # 이력에서 분석 키 + 경기유형 추출
    history_map = {}
    for hkey, entries in history.items():
        parts = hkey.rsplit('_', 1)
        if len(parts) == 2:
            analysis_key, game_type = parts
        else:
            analysis_key = hkey
            game_type = 'regular'
        if analysis_key in target_keys:
            history_map.setdefault(analysis_key, {})[game_type] = entries

    active_keys = [k for k in target_keys if k in history_map]
    if not active_keys:
        print("  대상 분석의 실행 이력이 없습니다.")
        return

    db = DBConnector()
    try:
        print("\n" + "=" * 70)
        print("  경기 분석 컬럼 생성")
        print("=" * 70)

        results = []
        for key in active_keys:
            for game_type, entries in history_map[key].items():
                meta = ANALYSIS_META.get(key, {})
                gt_label = get_game_type_label(game_type)

                print(f"\n  [{key}] {meta.get('title_template', key).format(teams='...')} ({gt_label})")

                title, content, chart_json, summary, team_ids = \
                    generate_column_html(key, entries, db, game_type)

                chart_count = 0
                if chart_json:
                    try:
                        chart_count = len(json.loads(chart_json))
                    except Exception:
                        pass

                print(f"    제목: {title}")
                print(f"    팀: {', '.join(e['team_name'] for e in entries)}")
                print(f"    본문: {len(content)}자 | 차트: {chart_count}개")

                if args.dry_run:
                    print(f"    [dry-run] DB 저장 건너뜀")
                    results.append((key, title, 'dry-run', 0))
                else:
                    first_tid = team_ids[0] if team_ids else None
                    column_id, action = save_column(
                        db, title, content, chart_json, summary,
                        meta.get('category', 'game'), first_tid
                    )
                    print(f"    -> {action} (column_id: {column_id})")
                    results.append((key, title, action, column_id))

        # 결과 요약
        print("\n" + "=" * 70)
        print("  컬럼 생성 결과")
        print("=" * 70)
        for key, title, action, cid in results:
            status = action if action == 'dry-run' else f'{action} (ID:{cid})'
            print(f"    [{key}] {status}")
        print(f"\n  총 {len(results)}건 {'미리보기' if args.dry_run else '저장'} 완료")
        print("=" * 70 + "\n")

    finally:
        db.close()


if __name__ == '__main__':
    main()
