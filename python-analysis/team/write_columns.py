"""
팀 분석 결과 기반 컬럼 기사 생성.
t1~t6 JSON 출력을 읽어 HTML 컬럼을 생성하고 analysis_column 테이블에 저장.

실행:
  python3 team/write_columns.py              # 전체 컬럼 생성
  python3 team/write_columns.py --analysis t1,t3  # 특정 분석만
  python3 team/write_columns.py --dry-run    # 미리보기 (DB 저장 안함)
"""
import sys
import os
import json
import argparse
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.db_connector import DBConnector
from team.team_common import LATEST_SEASON

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')
HISTORY_PATH = os.path.join(OUTPUT_DIR, 'run_history.json')
SEASON = LATEST_SEASON

# ==================== 분석별 메타데이터 ====================

ANALYSIS_META = {
    't1': {
        'title_template': '팀 전력 레이더: {teams}',
        'subtitle': '6축 지표로 본 구단 종합 전력 비교',
        'category': 'team',
        'icon': '🎯',
        'description': 'OPS, HR, ERA, FPCT, SB, OBP 6축 레이더로 팀 전력을 종합 분석합니다.',
    },
    't2': {
        'title_template': '상대전적 분석: {teams}',
        'subtitle': '10개 구단 상대전적 승률 & 천적/호구 분석',
        'category': 'team',
        'icon': '⚔️',
        'description': '팀별 상대전적 승률 매트릭스와 천적/호구 관계를 분석합니다.',
    },
    't3': {
        'title_template': '순위 변동 추이: {teams}',
        'subtitle': '6시즌(2020~2025) 순위 변동과 승률 추세',
        'category': 'team',
        'icon': '📊',
        'description': '6시즌 순위 변동 추이와 전년 대비 상승/하락을 분석합니다.',
    },
    't4': {
        'title_template': '타투 밸런스: {teams}',
        'subtitle': '공격력 vs 투수력 균형도 분석',
        'category': 'team',
        'icon': '⚖️',
        'description': '팀의 공격/투수 점수 밸런스를 비교하고 유형을 분류합니다.',
    },
    't5': {
        'title_template': '홈/원정 성적: {teams}',
        'subtitle': '홈 어드밴티지와 원정 성적 격차 분석',
        'category': 'team',
        'icon': '🏟️',
        'description': '홈/원정 승률 차이와 구장별 성적을 분석합니다.',
    },
    't6': {
        'title_template': '구단 효율 분석: {teams}',
        'subtitle': '피타고리안 승률 대비 실제 성과 효율성',
        'category': 'team',
        'icon': '💡',
        'description': '피타고리안 승률과 실제 승률의 괴리도를 통해 구단 효율을 분석합니다.',
    },
}


# ==================== 콘텐츠 생성 ====================

def load_history() -> dict:
    if os.path.exists(HISTORY_PATH):
        with open(HISTORY_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}


def load_team_charts(key: str, team_name: str) -> list:
    filepath = os.path.join(OUTPUT_DIR, f'{key}_{team_name}_{SEASON}.json')
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


def build_team_card_html(info: dict) -> str:
    name = info.get('teamName', '?')
    return (
        f'<div style="background:#f8f9fa;border-radius:8px;padding:12px 16px;'
        f'margin-bottom:12px;border-left:4px solid #0f3460;">'
        f'<strong>{name}</strong>'
        f'</div>'
    )


def build_chart_summary_html(charts: list, team_name: str) -> str:
    html_parts = []

    for chart in charts:
        chart_type = chart.get('chartType', '')
        title = chart.get('title', '')

        if chart_type == 'line':
            datasets = chart.get('datasets', [])
            labels = chart.get('labels', [])
            for ds in datasets[:3]:
                data = ds.get('data', [])
                label = ds.get('label', '')
                if not data or not labels:
                    continue
                valid = [(l, d) for l, d in zip(labels, data) if d is not None]
                if len(valid) >= 2:
                    first_val = valid[0][1]
                    last_val = valid[-1][1]
                    if isinstance(first_val, (int, float)) and isinstance(last_val, (int, float)):
                        change = last_val - first_val
                        direction = '↑' if change > 0 else ('↓' if change < 0 else '→')
                        html_parts.append(
                            f'<span style="margin-right:16px;">'
                            f'<strong>{label}</strong> '
                            f'{valid[0][0]}: {first_val} {direction} '
                            f'{valid[-1][0]}: {last_val}'
                            f'</span>'
                        )

        elif chart_type == 'radar':
            datasets = chart.get('datasets', [])
            labels = chart.get('labels', [])
            for ds in datasets[:1]:
                data = ds.get('data', [])
                if labels and data:
                    items = [f'{l}: {d}' for l, d in zip(labels, data) if d is not None]
                    if items:
                        html_parts.append(
                            f'<span style="color:#6c757d;">{", ".join(items)}</span>'
                        )

        elif chart_type in ('bar', 'horizontalBar'):
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

    if html_parts:
        return (
            f'<div style="background:#fff3cd;border-radius:6px;padding:10px 14px;'
            f'margin:8px 0 16px;font-size:0.9rem;">'
            + '<br>'.join(html_parts)
            + '</div>'
        )
    return ''


def generate_column_html(key: str, history_entries: list, db) -> tuple:
    meta = ANALYSIS_META.get(key, {})
    team_names = [e['team_name'] for e in history_entries]
    team_ids = [e['team_id'] for e in history_entries]

    info_map = get_team_info_map(db, team_ids)

    title_teams = ', '.join(team_names[:3])
    title = meta.get('title_template', '{teams} 분석').format(teams=title_teams)
    title = f"[{SEASON}] {title}"

    all_charts = []
    for name in team_names:
        charts = load_team_charts(key, name)
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
        info = info_map.get(tid, {})

        parts.append(f'<h3 style="margin-top:2rem;">{i+1}. {tname}</h3>')

        if info:
            parts.append(build_team_card_html(info))

        team_charts = load_team_charts(key, tname)
        chart_summary = build_chart_summary_html(team_charts, tname)
        if chart_summary:
            parts.append(chart_summary)

        reason = entry.get('reason', '')
        if reason:
            parts.append(f'<p style="color:#495057;">{reason}</p>')

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

    summary = f'{SEASON} 시즌 {title_teams}의 {meta.get("subtitle", "분석")}.'

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
    parser = argparse.ArgumentParser(description='팀 분석 컬럼 생성기')
    parser.add_argument('--analysis', type=str, default=None,
                        help='컬럼 생성할 분석 (쉼표 구분, 예: t1,t3)')
    parser.add_argument('--dry-run', action='store_true',
                        help='미리보기만 (DB 저장 안함)')
    args = parser.parse_args()

    history = load_history()
    if not history:
        print("  실행 이력이 없습니다. 먼저 'python3 team/run_all.py'를 실행하세요.")
        return

    if args.analysis:
        target_keys = [k.strip().lower() for k in args.analysis.split(',')]
    else:
        target_keys = sorted(ANALYSIS_META.keys())

    target_keys = [k for k in target_keys if k in history]
    if not target_keys:
        print("  대상 분석의 실행 이력이 없습니다.")
        return

    db = DBConnector()
    try:
        print("\n" + "=" * 70)
        print("  팀 분석 컬럼 생성")
        print("=" * 70)

        results = []
        for key in target_keys:
            entries = history[key]
            meta = ANALYSIS_META.get(key, {})

            print(f"\n  [{key}] {meta.get('icon', '')} "
                  f"{meta.get('title_template', key).format(teams='...')}")

            title, content, chart_json, summary, team_ids = \
                generate_column_html(key, entries, db)

            chart_count = 0
            if chart_json:
                try:
                    chart_count = len(json.loads(chart_json))
                except:
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
                    meta.get('category', 'team'), first_tid
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
