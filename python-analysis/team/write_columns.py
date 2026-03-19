"""
팀 분석 결과 기반 컬럼 기사 생성.
t1~t6 JSON 출력을 읽어 팀별 기사형 HTML 컬럼을 생성하고 analysis_column 테이블에 저장.
분석 주제당 10개 팀 각각 개별 기사를 생성한다.

실행:
  python3 team/write_columns.py              # 전체 컬럼 생성
  python3 team/write_columns.py --analysis t1,t3  # 특정 분석만
  python3 team/write_columns.py --dry-run    # 미리보기 (DB 저장 안함)
"""
import sys
import os
import json
import argparse

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.db_connector import DBConnector
from common.article_generator import generate_article
from team.team_common import TEAM_NAMES
from config import DATA_SEASON, PREDICT_SEASON

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')
HISTORY_PATH = os.path.join(OUTPUT_DIR, 'run_history.json')
SEASON = DATA_SEASON


def get_ranking_map(db, season: int) -> dict:
    """팀 ID -> 순위 매핑"""
    rankings = db.get_team_rankings(season)
    if rankings.empty:
        return {}
    return {int(row['teamId']): int(row['ranking'])
            for _, row in rankings.iterrows()}


# ==================== 분석별 메타데이터 ====================

ANALYSIS_META = {
    't1': {
        'title_template': '팀 전력 레이더: {teams}',
        'subtitle': '6축 지표로 본 구단 종합 전력 비교',
        'category': 'team',
        'description': 'OPS, HR, ERA, FPCT, SB, OBP 6축 레이더로 팀 전력을 종합 분석합니다.',
    },
    't2': {
        'title_template': '상대전적 분석: {teams}',
        'subtitle': '10개 구단 상대전적 승률 & 천적/호구 분석',
        'category': 'team',
        'description': '팀별 상대전적 승률 매트릭스와 천적/호구 관계를 분석합니다.',
    },
    't3': {
        'title_template': '순위 변동 추이: {teams}',
        'subtitle': '6시즌(2020~2025) 순위 변동과 승률 추세',
        'category': 'team',
        'description': '6시즌 순위 변동 추이와 전년 대비 상승/하락을 분석합니다.',
    },
    't4': {
        'title_template': '타투 밸런스: {teams}',
        'subtitle': '공격력 vs 투수력 균형도 분석',
        'category': 'team',
        'description': '팀의 공격/투수 점수 밸런스를 비교하고 유형을 분류합니다.',
    },
    't5': {
        'title_template': '홈/원정 성적: {teams}',
        'subtitle': '홈 어드밴티지와 원정 성적 격차 분석',
        'category': 'team',
        'description': '홈/원정 승률 차이와 구장별 성적을 분석합니다.',
    },
    't6': {
        'title_template': '구단 효율 분석: {teams}',
        'subtitle': '피타고리안 승률 대비 실제 성과 효율성',
        'category': 'team',
        'description': '피타고리안 승률과 실제 승률의 괴리도를 통해 구단 효율을 분석합니다.',
    },
}


# ==================== 콘텐츠 생성 ====================

def load_history() -> dict:
    if os.path.exists(HISTORY_PATH):
        with open(HISTORY_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}


def load_analysis_output(key: str, team_name: str) -> dict:
    """분석 출력 JSON 로드."""
    filepath = os.path.join(OUTPUT_DIR, f'{key}_{team_name}_{SEASON}.json')
    if not os.path.exists(filepath):
        return {'charts': [], 'findings': [], 'stats': {}}
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    if isinstance(data, list):
        return {'charts': data, 'findings': [], 'stats': {}}
    return data


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


def build_team_card_html(info: dict, rank: int = None) -> str:
    name = info.get('teamName', '?')
    rank_str = f' | {rank}위' if rank else ''
    return (
        f'<div style="background:#f8f9fa;border-radius:8px;padding:12px 16px;'
        f'margin-bottom:12px;border-left:4px solid #0f3460;">'
        f'<strong>{name}</strong>'
        f'<span style="color:#6c757d;">{rank_str}</span>'
        f'</div>'
    )


def generate_column_html(key: str, entry: dict, db,
                         ranking_map: dict) -> tuple:
    """팀 1개에 대한 개별 기사 HTML 생성."""
    meta = ANALYSIS_META.get(key, {})
    tid = entry['team_id']
    tname = entry['team_name']

    info_map = get_team_info_map(db, [tid])
    info = info_map.get(tid, {})
    rank = ranking_map.get(tid)

    base_title = meta.get('title_template', '{teams} 분석').format(teams=tname)
    title = f"[{PREDICT_SEASON} 시즌 전망] {base_title}"

    # JSON 파일은 짧은 이름(LG, KIA 등)으로 저장됨
    short_name = TEAM_NAMES.get(tid, tname)
    output = load_analysis_output(key, short_name)

    entries_data = [{
        'name': tname,
        'card_html': build_team_card_html(info, rank) if info else '',
        'findings': output.get('findings', []),
        'charts': output.get('charts', []),
    }]

    content_html, all_charts, ai_summary = generate_article(
        key, entries_data, meta,
        data_season=SEASON, predict_season=PREDICT_SEASON
    )

    chart_json_str = json.dumps(all_charts, ensure_ascii=False) if all_charts else None

    summary = ai_summary or \
        f'{SEASON} 시즌 데이터 기반 {tname}의 {PREDICT_SEASON} 시즌 {meta.get("subtitle", "전망")}.'

    return title, content_html, chart_json_str, summary, tid


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
        ranking_map = get_ranking_map(db, SEASON)
        if ranking_map:
            print(f"\n  [순위] {SEASON} 시즌 팀 순위 로딩 완료 ({len(ranking_map)}팀)")

        print("\n" + "=" * 70)
        print("  팀 분석 컬럼 생성 (팀별 개별 기사)")
        print("=" * 70)

        results = []
        for key in target_keys:
            entries = history[key]
            meta = ANALYSIS_META.get(key, {})

            # 순위순 정렬
            entries.sort(key=lambda e: ranking_map.get(e['team_id'], 99))

            print(f"\n  [{key}] {meta.get('title_template', key).format(teams='...')} "
                  f"({len(entries)}팀)")

            for entry in entries:
                tname = entry['team_name']
                rank = ranking_map.get(entry['team_id'], '?')
                print(f"\n    [{tname} ({rank}위)]")

                title, content, chart_json, summary, tid = \
                    generate_column_html(key, entry, db, ranking_map)

                chart_count = 0
                if chart_json:
                    try:
                        chart_count = len(json.loads(chart_json))
                    except Exception:
                        pass

                print(f"      제목: {title}")
                print(f"      본문: {len(content)}자 | 차트: {chart_count}개")

                if args.dry_run:
                    print(f"      [dry-run] DB 저장 건너뜀")
                    results.append((key, tname, 'dry-run', 0))
                else:
                    column_id, action = save_column(
                        db, title, content, chart_json, summary,
                        meta.get('category', 'team'), tid
                    )
                    print(f"      -> {action} (column_id: {column_id})")
                    results.append((key, tname, action, column_id))

        # 결과 요약
        print("\n" + "=" * 70)
        print("  컬럼 생성 결과")
        print("=" * 70)
        for key, name, action, cid in results:
            status = action if action == 'dry-run' else f'{action} (ID:{cid})'
            print(f"    [{key}] {name}: {status}")
        print(f"\n  총 {len(results)}건 {'미리보기' if args.dry_run else '저장'} 완료")
        print("=" * 70 + "\n")

    finally:
        db.close()


if __name__ == '__main__':
    main()
