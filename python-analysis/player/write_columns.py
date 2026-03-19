"""
선수 분석 결과 기반 컬럼 기사 생성.
p1~p9 JSON 출력을 읽어 HTML 컬럼을 생성하고 analysis_column 테이블에 저장.

실행:
  python3 player/write_columns.py              # 전체 컬럼 생성
  python3 player/write_columns.py --analysis p1,p3  # 특정 분석만
  python3 player/write_columns.py --dry-run    # 미리보기 (DB 저장 안함)
"""
import sys
import os
import json
import argparse
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.db_connector import DBConnector

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')
HISTORY_PATH = os.path.join(OUTPUT_DIR, 'run_history.json')
SEASON = 2025

# ==================== 분석별 메타데이터 ====================

ANALYSIS_META = {
    'p1': {
        'title_template': '시즌 트렌드 분석: {players}의 성장 궤적',
        'subtitle': '6시즌(2020~2025) 데이터로 본 선수별 성적 변화',
        'category': 'player',
        'icon': '📈',
        'description': '다시즌 데이터를 기반으로 주요 지표의 상승·하락 추이와 피크 시즌을 분석합니다.',
    },
    'p2': {
        'title_template': '세이버메트릭스 프로파일: {players}',
        'subtitle': '리그 평균 대비 세이버 지표 위치와 백분위 분석',
        'category': 'player',
        'icon': '🔬',
        'description': 'ISO, K%, BB%, wOBA 등 세이버 지표로 선수의 타격 특성을 프로파일링합니다.',
    },
    'p3': {
        'title_template': '타자 유형 분류: 파워·컨택·스피드',
        'subtitle': 'z-score 기반 4축 분석으로 본 타자 유형',
        'category': 'player',
        'icon': '⚾',
        'description': 'ISO, K%, BB%, SB 4축 z-score로 타자를 파워형·컨택형·스피드형으로 분류합니다.',
    },
    'p4': {
        'title_template': '투수 유형 분류: 탈삼진·땅볼·제구',
        'subtitle': 'K/9, BB/9, GO/AO 분석으로 본 투수 유형',
        'category': 'player',
        'icon': '🔥',
        'description': '투수 5축 지표를 통해 탈삼진형·땅볼형·제구형 투수를 분류합니다.',
    },
    'p5': {
        'title_template': '에이징 커브: {players}의 나이 vs 성적',
        'subtitle': '베테랑 선수의 나이-성적 곡선 분석',
        'category': 'player',
        'icon': '📊',
        'description': '오랜 커리어를 가진 베테랑의 에이징 커브를 리그 평균과 비교합니다.',
    },
    'p6': {
        'title_template': '경기별 변동성: {players}의 일관성 분석',
        'subtitle': '경기 단위 성적 편차와 시계열 패턴',
        'category': 'player',
        'icon': '📉',
        'description': '경기별 타율 변동계수, 월별 추이, 안타 분포를 분석합니다.',
    },
    'p7': {
        'title_template': '포지션별 공격력: 포수·내야·외야 대표',
        'subtitle': '포지션 내 OPS 랭킹과 전 포지션 비교',
        'category': 'player',
        'icon': '🏟️',
        'description': '각 포지션 대표 타자의 공격력을 포지션 평균 및 리그와 비교합니다.',
    },
    'p8': {
        'title_template': '투타 유형별 성적: 좌타·우타·양타 대표',
        'subtitle': '타석 유형별 OPS 분포와 유형 내 랭킹',
        'category': 'player',
        'icon': '🏏',
        'description': '좌타·우타·양타 유형별 성적 차이와 각 유형 대표 선수를 분석합니다.',
    },
    'p9': {
        'title_template': 'BABIP 운 분석: 행운과 불운의 경계',
        'subtitle': 'BABIP-AVG 괴리도와 평균 회귀 전망',
        'category': 'player',
        'icon': '🍀',
        'description': 'BABIP 기반 행운 지수로 실력과 운의 비율을 분석합니다.',
    },
}


# ==================== 콘텐츠 생성 ====================

def load_history() -> dict:
    """실행 이력 로드"""
    if os.path.exists(HISTORY_PATH):
        with open(HISTORY_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}


def load_player_charts(key: str, player_name: str) -> list:
    """특정 분석-선수의 차트 JSON 로드"""
    filepath = os.path.join(OUTPUT_DIR, f'{key}_{player_name}_{SEASON}.json')
    if os.path.exists(filepath):
        with open(filepath, 'r', encoding='utf-8') as f:
            return json.load(f)
    return []


def get_player_info_map(db, player_ids: list) -> dict:
    """선수 ID 목록 → {id: info_dict} 매핑"""
    if not player_ids:
        return {}
    placeholders = ','.join(['%s'] * len(player_ids))
    sql = f"""
        SELECT p.id AS playerId, p.name, p.position, p.birthDate,
               p.throwBat, p.backNumber, t.name AS teamName
        FROM player p
        JOIN team t ON p.teamId = t.id
        WHERE p.id IN ({placeholders})
    """
    df = db.query(sql, tuple(player_ids))
    result = {}
    for _, row in df.iterrows():
        result[int(row['playerId'])] = row.to_dict()
    return result


def build_player_card_html(info: dict) -> str:
    """선수 정보 카드 HTML"""
    name = info.get('name') or '?'
    team = info.get('teamName') or '?'
    pos = info.get('position') or '미확인'
    tb = info.get('throwBat') or '미확인'
    num = info.get('backNumber') or '?'
    return (
        f'<div style="background:#f8f9fa;border-radius:8px;padding:12px 16px;'
        f'margin-bottom:12px;border-left:4px solid #0f3460;">'
        f'<strong>{name}</strong> '
        f'<span style="color:#6c757d;">({team}) #{num} | {pos} | {tb}</span>'
        f'</div>'
    )


def build_chart_summary_html(charts: list, player_name: str) -> str:
    """차트 데이터에서 핵심 수치 추출하여 HTML 생성"""
    html_parts = []

    for chart in charts:
        chart_type = chart.get('chartType', '')
        title = chart.get('title', '')

        if chart_type == 'line':
            # 라인 차트: 추이 요약
            datasets = chart.get('datasets', [])
            labels = chart.get('labels', [])
            for ds in datasets[:3]:  # 최대 3개 데이터셋
                data = ds.get('data', [])
                label = ds.get('label', '')
                if not data or not labels:
                    continue
                # None/null 제거
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
            # 레이더 차트: 축별 수치 나열
            datasets = chart.get('datasets', [])
            labels = chart.get('labels', [])
            for ds in datasets[:1]:  # 첫 번째만
                data = ds.get('data', [])
                if labels and data:
                    items = [f'{l}: {d}' for l, d in zip(labels, data) if d is not None]
                    if items:
                        html_parts.append(
                            f'<span style="color:#6c757d;">{", ".join(items)}</span>'
                        )

        elif chart_type in ('bar', 'horizontalBar'):
            # 바 차트: Top 값 추출
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
    """
    분석 키에 대한 컬럼 HTML 생성.
    반환: (title, content_html, chart_json, summary, player_ids)
    """
    meta = ANALYSIS_META.get(key, {})
    player_names = [e['player_name'] for e in history_entries]
    player_ids = [e['player_id'] for e in history_entries]

    # 선수 정보 조회
    info_map = get_player_info_map(db, player_ids)

    # 제목 생성
    title_players = ', '.join(player_names[:3])
    title = meta.get('title_template', '{players} 분석').format(players=title_players)
    title = f"[{SEASON}] {title}"

    # 차트 데이터 수집
    all_charts = []
    for name in player_names:
        charts = load_player_charts(key, name)
        all_charts.extend(charts)

    # HTML 본문 생성
    parts = []

    # 도입부
    subtitle = meta.get('subtitle', '')
    description = meta.get('description', '')
    parts.append(f'<p><strong>{subtitle}</strong></p>')
    parts.append(f'<p>{description}</p>')
    parts.append('<hr style="margin:1.5rem 0;">')

    # 선수별 섹션
    for i, entry in enumerate(history_entries):
        pid = entry['player_id']
        pname = entry['player_name']
        info = info_map.get(pid, {})

        parts.append(f'<h3 style="margin-top:2rem;">{i+1}. {pname}</h3>')

        # 선수 카드
        if info:
            parts.append(build_player_card_html(info))

        # 차트 요약 (해당 선수 차트만)
        player_charts = load_player_charts(key, pname)
        chart_summary = build_chart_summary_html(player_charts, pname)
        if chart_summary:
            parts.append(chart_summary)

        # 추천 이유
        reason = entry.get('reason', '')
        if reason:
            parts.append(f'<p style="color:#495057;">{reason}</p>')

        # 차트 수 안내
        chart_count = len(player_charts)
        if chart_count > 0:
            chart_titles = [c.get('title', '') for c in player_charts if c.get('title')]
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
        f'본 분석은 glvpen Python 배치 분석 시스템으로 자동 생성되었습니다.<br>'
        f'분석 일시: {datetime.now().strftime("%Y년 %m월 %d일")}</p>'
    )

    content_html = '\n'.join(parts)
    chart_json_str = json.dumps(all_charts, ensure_ascii=False) if all_charts else None

    # 요약 생성
    summary = f'{SEASON} 시즌 {title_players}의 {meta.get("subtitle", "분석")}.'

    return title, content_html, chart_json_str, summary, player_ids


# ==================== DB 저장 ====================

def save_column(db, title, content, chart_json, summary,
                category, player_id=None, season=SEASON):
    """analysis_column 테이블에 INSERT"""
    cursor = db.conn.cursor()

    # 중복 방지: 같은 제목+시즌이 있으면 UPDATE
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
                (title, content, category, relatedPlayerId, season,
                 chartData, publishDate, viewCount, autoGenerated, summary, featured)
            VALUES (%s, %s, %s, %s, %s, %s, NOW(), 0, TRUE, %s, FALSE)
        """, (title, content, category, player_id, season, chart_json, summary))
        column_id = cursor.lastrowid
        action = "INSERT"

    db.conn.commit()
    cursor.close()
    return column_id, action


# ==================== 메인 ====================

def main():
    parser = argparse.ArgumentParser(description='선수 분석 컬럼 생성기')
    parser.add_argument('--analysis', type=str, default=None,
                        help='컬럼 생성할 분석 (쉼표 구분, 예: p1,p3)')
    parser.add_argument('--dry-run', action='store_true',
                        help='미리보기만 (DB 저장 안함)')
    args = parser.parse_args()

    # 이력 로드
    history = load_history()
    if not history:
        print("  실행 이력이 없습니다. 먼저 'python3 player/run_all.py'를 실행하세요.")
        return

    # 대상 분석 결정
    if args.analysis:
        target_keys = [k.strip().lower() for k in args.analysis.split(',')]
    else:
        target_keys = sorted(ANALYSIS_META.keys())

    # 이력에 있는 분석만 필터
    target_keys = [k for k in target_keys if k in history]
    if not target_keys:
        print("  대상 분석의 실행 이력이 없습니다.")
        return

    db = DBConnector()
    try:
        print("\n" + "=" * 70)
        print("  선수 분석 컬럼 생성")
        print("=" * 70)

        results = []
        for key in target_keys:
            entries = history[key]
            meta = ANALYSIS_META.get(key, {})

            print(f"\n  [{key}] {meta.get('icon', '')} "
                  f"{meta.get('title_template', key).format(players='...')}")

            title, content, chart_json, summary, player_ids = \
                generate_column_html(key, entries, db)

            chart_count = 0
            if chart_json:
                try:
                    chart_count = len(json.loads(chart_json))
                except:
                    pass

            print(f"    제목: {title}")
            print(f"    선수: {', '.join(e['player_name'] for e in entries)}")
            print(f"    본문: {len(content)}자 | 차트: {chart_count}개")

            if args.dry_run:
                print(f"    [dry-run] DB 저장 건너뜀")
                results.append((key, title, 'dry-run', 0))
            else:
                # 첫 번째 선수 ID를 related_player_id로
                first_pid = player_ids[0] if player_ids else None
                column_id, action = save_column(
                    db, title, content, chart_json, summary,
                    meta.get('category', 'player'), first_pid
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
