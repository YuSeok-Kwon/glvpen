"""
기사형 분석 컬럼 HTML 생성 엔진.
findings + 차트 데이터를 조합하여 서술적 기사 HTML을 생성한다.

생성 순서:
  1) Gemini API로 자연스러운 매거진 기사 생성 시도
  2) 실패 시 기존 regex 기반 fallback 로직 사용
"""
import re
import json
from datetime import datetime

from common.gemini_client import gemini_client


# ==================== Gemini 프롬프트 빌더 ====================

def _build_gemini_prompt(key: str, entries_data: list,
                         meta: dict, data_season: int = None,
                         predict_season: int = None) -> str:
    """Gemini API 호출용 프롬프트 구성."""
    subtitle = meta.get('subtitle', '')
    description = meta.get('description', '')

    # 시즌 프레이밍: "2025 데이터 → 2026 시즌 전망"
    if data_season and predict_season:
        season_context = (
            f'데이터 시즌: {data_season}\n'
            f'전망 시즌: {predict_season}\n'
            f'기사 성격: {data_season} 시즌 데이터를 기반으로 {predict_season} 시즌을 전망하는 분석 기사'
        )
    elif data_season:
        season_context = f'시즌: {data_season}'
    else:
        season_context = ''

    # 분석 대상별 데이터 정리
    entity_sections = []
    chart_index = 0
    for entry in entries_data:
        name = entry['name']
        findings = entry.get('findings', [])
        stats = entry.get('stats', {})
        charts = entry.get('charts', [])

        lines = [f'### {name}']
        if findings:
            lines.append('발견사항:')
            for f in findings:
                lines.append(f'- {f}')
        if stats:
            lines.append('주요 통계:')
            for sk, sv in stats.items():
                lines.append(f'- {sk}: {sv}')

        # 차트 목록
        for c in charts:
            ct = c.get('chartType', '?')
            ct_title = c.get('title', '차트')
            lines.append(f'CHART:{chart_index} - {ct_title} ({ct})')
            chart_index += 1

        entity_sections.append('\n'.join(lines))

    entities_text = '\n\n'.join(entity_sections)

    prompt = f"""당신은 KBO 야구 데이터 분석 매거진 기사 작성 전문가입니다.
아래 데이터를 바탕으로 전문적이고 자연스러운 분석 기사를 작성해 주세요.

=== 분석 주제 ===
{subtitle}
{description}
{season_context}

=== 분석 대상 ===
{entities_text}

=== 작성 규칙 ===
1. 본문은 HTML 형식으로 작성 (<p>, <strong>, <h3> 태그 사용)
2. 각 분석 대상(선수/팀)별로 <h3>N. 이름</h3> 섹션으로 구성
3. 차트를 언급할 때 해당 위치에 <!-- CHART:N --> 마커를 삽입 (프론트엔드가 자동 렌더링)
   - 반드시 위 데이터에 있는 CHART 번호만 사용
4. {data_season} 시즌 데이터를 분석하되, {predict_season} 시즌 전망·예측에 초점을 맞출 것
5. 도입(리드문) → 각 대상 분석 → 종합 비교 → {predict_season} 시즌 전망 구성의 매거진 기사체
6. 수치를 정확히 인용하되, 독자가 이해하기 쉬운 자연스러운 문장으로 풀어서 서술
7. 가설검정 결과(p값)가 있으면 독자가 이해하기 쉽게 설명
8. 800~1500자 분량
9. 존댓말(~습니다, ~입니다) 사용
10. 마지막에 ##SUMMARY## 구분자를 넣고, 그 뒤에 2줄 이내 요약 (100자 이내)

=== 금지 사항 ===
- 마크다운(#, **, - 등) 사용 금지 — HTML 태그만 사용
- ```html 코드블록 감싸기 금지 — 순수 HTML 텍스트만 출력
- 데이터에 없는 수치를 임의로 생성하지 말 것
"""
    return prompt


def _validate_ai_response(text: str) -> bool:
    """AI 응답 품질 검증."""
    if not text or len(text) < 200:
        return False
    # Gemini 에러 메시지 감지
    error_keywords = ['죄송합니다', 'I cannot', 'I apologize', 'error']
    if any(text.strip().startswith(kw) for kw in error_keywords):
        return False
    # 최소한의 HTML 구조 확인
    if '<p>' not in text and '<h3>' not in text:
        return False
    return True


def _parse_ai_response(ai_text: str, entries_data: list) -> tuple:
    """
    Gemini 응답에서 본문/요약 분리 + 카드 HTML 삽입.
    반환: (article_html, summary)
    """
    # 코드블록 래퍼 제거
    text = ai_text.strip()
    if text.startswith('```html'):
        text = text[7:]
    if text.startswith('```'):
        text = text[3:]
    if text.endswith('```'):
        text = text[:-3]
    text = text.strip()

    # ##SUMMARY## 구분자로 본문/요약 분리
    summary = None
    if '##SUMMARY##' in text:
        parts = text.split('##SUMMARY##', 1)
        text = parts[0].strip()
        summary = parts[1].strip()
        # HTML 태그 제거한 순수 텍스트 요약
        summary = re.sub(r'<[^>]+>', '', summary).strip()
        if len(summary) > 150:
            summary = summary[:147] + '...'

    # 엔티티 카드 HTML을 각 <h3> 섹션 바로 뒤에 삽입
    for entry in entries_data:
        name = entry['name']
        card_html = entry.get('card_html', '')
        if not card_html:
            continue
        # <h3>N. 이름</h3> 패턴 뒤에 카드 삽입
        pattern = re.compile(
            rf'(<h3[^>]*>\s*\d+\.\s*{re.escape(name)}\s*</h3>)',
            re.IGNORECASE
        )
        if pattern.search(text):
            text = pattern.sub(rf'\1\n{card_html}', text, count=1)

    return text, summary


# ==================== fallback: findings 서술화 ====================

# 패턴: "KEY: VALUE" 형태를 서술형으로 변환
_FINDING_PATTERNS = [
    # 피크 시즌
    (re.compile(r'^피크 시즌:\s*(\d{4})년\s*\((.+?)\)$'),
     lambda m: f'피크는 {m.group(1)}년으로, {m.group(2)}를 기록했다.'),
    # 상관계수/성장 방향
    (re.compile(r'^(\S+)\s+성장 방향:\s*(.+)$'),
     lambda m: f'{m.group(1)} 지표는 {m.group(2)}.'),
    # 리그 평균 대비 우수/열세
    (re.compile(r'^(\S+)\s+리그\s*평균\s*대비\s*(우수|열세)\s*\(([^)]+)\)$'),
     lambda m: f'{m.group(1)}은(는) 리그 평균 대비 {m.group(2)}한 수준이다({m.group(3)}).'),
    # 종합 전력 N위
    (re.compile(r'^(\S+)\s+종합\s*전력\s*(\d+)위\s*\((.+?)\)$'),
     lambda m: f'{m.group(1)}의 종합 전력은 {m.group(2)}위({m.group(3)})에 위치한다.'),
    # 상위/하위 그룹
    (re.compile(r'^상위/하위\s+그룹\s+간\s+(.+)$'),
     lambda m: f'상위와 하위 그룹 간에는 {m.group(1)}.'),
    # 전반 득점 비중
    (re.compile(r'^전반\s+득점\s+비중:\s*([\d.]+)%\s*\((.+?)\)$'),
     lambda m: f'전반 득점 비중은 {m.group(1)}%이며, {m.group(2)}.'),
    # 최다 득점 이닝
    (re.compile(r'^(\S+)\s+최다\s+득점\s+이닝:\s*(\d+)회\s*\((.+?)\)$'),
     lambda m: f'{m.group(1)}은(는) {m.group(2)}회에서 가장 많은 점수를 올렸다({m.group(3)}).'),
    # 전반 vs 후반
    (re.compile(r'^전반\s+vs\s+후반\s+득점:\s*(.+)$'),
     lambda m: f'전반과 후반 득점을 비교하면, {m.group(1)}.'),
    # 백분위
    (re.compile(r'^(\S+)\s+백분위:\s*([\d.]+)%\s*\((.+?)\)$'),
     lambda m: f'{m.group(1)}의 리그 내 백분위는 {m.group(2)}%이다({m.group(3)}).'),
    # TYPE: description 패턴 (제목형)
    (re.compile(r'^(\S+)\s+유형:\s*(.+)$'),
     lambda m: f'분석 결과 {m.group(1)} 유형은 "{m.group(2)}"에 해당한다.'),
    # 일반적 "X: Y" 패턴 (폴백)
    (re.compile(r'^([^:]+):\s+(.+)$'),
     lambda m: f'{m.group(1).strip()}은(는) {m.group(2).strip()}이다.'),
]


def _narrativize(finding: str) -> str:
    """단일 finding 문장을 서술형으로 변환. 매칭 안 되면 원문 반환."""
    for pattern, rewriter in _FINDING_PATTERNS:
        match = pattern.match(finding.strip())
        if match:
            return rewriter(match)
    # 이미 서술형이면 그대로
    return finding.strip()


def findings_to_paragraphs(findings: list, chunk_size: int = 3) -> list:
    """
    findings 리스트를 서술형 문단(paragraph) 리스트로 변환.
    chunk_size개씩 묶어 하나의 <p> 태그로 생성.
    """
    if not findings:
        return []

    narratives = [_narrativize(f) for f in findings]
    paragraphs = []
    for i in range(0, len(narratives), chunk_size):
        chunk = narratives[i:i + chunk_size]
        text = ' '.join(chunk)
        paragraphs.append(text)
    return paragraphs


# ==================== 차트 해석 ====================

def interpret_chart(chart: dict) -> str:
    """차트 데이터에서 핵심 해석 문장을 생성."""
    chart_type = chart.get('chartType', '')
    title = chart.get('title', '')
    datasets = chart.get('datasets', [])
    labels = chart.get('labels', [])

    if chart_type == 'line' and datasets:
        ds = datasets[0]
        data = ds.get('data', [])
        label = ds.get('label', '')
        valid = [(l, d) for l, d in zip(labels, data) if d is not None]
        if len(valid) >= 2:
            first_l, first_v = valid[0]
            last_l, last_v = valid[-1]
            if isinstance(first_v, (int, float)) and isinstance(last_v, (int, float)):
                diff = last_v - first_v
                if diff > 0:
                    return (f'{label}은(는) {first_l}({first_v})에서 '
                            f'{last_l}({last_v})로 상승 추세를 보인다.')
                elif diff < 0:
                    return (f'{label}은(는) {first_l}({first_v})에서 '
                            f'{last_l}({last_v})로 하락 추세를 보인다.')
                else:
                    return f'{label}은(는) {first_l}~{last_l} 기간 동안 변화가 없다.'

    elif chart_type == 'radar' and datasets and labels:
        ds = datasets[0]
        data = ds.get('data', [])
        if data:
            pairs = list(zip(labels, data))
            best = max(pairs, key=lambda x: x[1] if isinstance(x[1], (int, float)) else 0)
            worst = min(pairs, key=lambda x: x[1] if isinstance(x[1], (int, float)) else 100)
            return f'강점은 {best[0]}({best[1]}), 약점은 {worst[0]}({worst[1]})이다.'

    elif chart_type in ('bar', 'horizontalBar') and datasets and labels:
        ds = datasets[0]
        data = ds.get('data', [])
        pairs = [(l, d) for l, d in zip(labels, data)
                 if d is not None and isinstance(d, (int, float))]
        if pairs:
            top = max(pairs, key=lambda x: x[1])
            return f'가장 높은 항목은 {top[0]}({top[1]})이다.'

    elif chart_type == 'doughnut' and datasets and labels:
        data = datasets[0].get('data', [])
        total = sum(d for d in data if isinstance(d, (int, float)))
        if total > 0:
            pairs = [(l, d) for l, d in zip(labels, data) if isinstance(d, (int, float))]
            top = max(pairs, key=lambda x: x[1])
            pct = round(top[1] / total * 100, 1)
            return f'가장 큰 비중은 {top[0]}({pct}%)이다.'

    return ''


# ==================== fallback: 기사 구조 빌더 ====================

def build_intro(key: str, meta: dict, entity_names: list,
                data_season: int = None, predict_season: int = None) -> str:
    """분석 주제별 도입부 문장 생성."""
    description = meta.get('description', '')
    names_str = ', '.join(entity_names[:3])
    if len(entity_names) > 3:
        names_str += f' 외 {len(entity_names) - 3}명'

    if data_season and predict_season:
        intro = (f'{data_season} 시즌 {names_str}의 데이터를 분석하여 '
                 f'{predict_season} 시즌을 전망했다.')
    elif data_season:
        intro = f'{data_season} 시즌 {names_str}의 데이터를 분석했다.'
    else:
        intro = f'{names_str}의 데이터를 분석했다.'

    return (
        f'<p class="lead" style="font-size:1.1rem;color:#333;line-height:1.8;">'
        f'{intro} {description}</p>'
    )


def build_conclusion(key: str, entity_names: list, all_findings: dict) -> str:
    """비교 요약 결론 생성."""
    if len(entity_names) <= 1:
        return ''

    parts = ['<h3 style="margin-top:2.5rem;padding-top:1rem;border-top:2px solid #e9ecef;">'
             '종합 비교</h3>']

    # 각 엔티티의 첫 번째 finding으로 요약
    summary_items = []
    for name in entity_names:
        flist = all_findings.get(name, [])
        if len(flist) > 1:
            # 첫 번째는 제목, 두 번째부터가 실제 인사이트
            summary_items.append(f'<strong>{name}</strong>: {_narrativize(flist[1])}')
        elif flist:
            summary_items.append(f'<strong>{name}</strong>: {_narrativize(flist[0])}')

    if summary_items:
        parts.append('<ul style="line-height:2.0;">')
        for item in summary_items:
            parts.append(f'<li>{item}</li>')
        parts.append('</ul>')

    return '\n'.join(parts)


def _build_entity_section(index: int, name: str, card_html: str,
                          findings: list, charts: list,
                          chart_offset: int) -> tuple:
    """
    단일 엔티티(선수/팀) 섹션 HTML 생성.
    반환: (section_html, 사용된 차트 수)
    """
    parts = []
    parts.append(f'<h3 style="margin-top:2rem;">{index + 1}. {name}</h3>')

    if card_html:
        parts.append(card_html)

    if not findings and not charts:
        parts.append(f'<p>{name}에 대한 상세 데이터를 확인해보세요.</p>')
        return '\n'.join(parts), 0

    # findings를 문단으로 변환
    # 첫 번째 finding은 소개 문장으로 사용
    intro_finding = findings[0] if findings else ''
    detail_findings = findings[1:] if len(findings) > 1 else []

    if intro_finding:
        parts.append(f'<p style="line-height:2.0;">{_narrativize(intro_finding)}</p>')

    # 차트와 findings를 교차 배치
    chart_idx = 0
    finding_chunks = []
    chunk_size = max(1, len(detail_findings) // max(len(charts), 1)) if charts else 3

    for i in range(0, len(detail_findings), max(chunk_size, 1)):
        finding_chunks.append(detail_findings[i:i + max(chunk_size, 1)])

    num_sections = max(len(finding_chunks), len(charts))

    for si in range(num_sections):
        # findings 문단
        if si < len(finding_chunks):
            chunk = finding_chunks[si]
            narratives = [_narrativize(f) for f in chunk]
            parts.append(f'<p style="line-height:2.0;">{" ".join(narratives)}</p>')

        # 차트 마커
        if chart_idx < len(charts):
            actual_idx = chart_offset + chart_idx
            parts.append(f'<!-- CHART:{actual_idx} -->')
            # 차트 해석 문장
            interp = interpret_chart(charts[chart_idx])
            if interp:
                parts.append(
                    f'<p class="text-muted" style="font-size:0.9rem;'
                    f'font-style:italic;margin-top:-0.5rem;">{interp}</p>'
                )
            chart_idx += 1

    # 남은 차트 (findings보다 차트가 많은 경우)
    while chart_idx < len(charts):
        actual_idx = chart_offset + chart_idx
        parts.append(f'<!-- CHART:{actual_idx} -->')
        interp = interpret_chart(charts[chart_idx])
        if interp:
            parts.append(
                f'<p class="text-muted" style="font-size:0.9rem;'
                f'font-style:italic;margin-top:-0.5rem;">{interp}</p>'
            )
        chart_idx += 1

    return '\n'.join(parts), chart_idx


def _generate_fallback(key: str, entries_data: list, meta: dict,
                       data_season: int = None,
                       predict_season: int = None) -> tuple:
    """기존 regex 기반 기사 생성 (Gemini 실패 시 fallback)."""
    entity_names = [e['name'] for e in entries_data]

    parts = []

    # 도입부
    parts.append(build_intro(key, meta, entity_names, data_season, predict_season))
    parts.append('<hr style="margin:1.5rem 0;">')

    # 전체 차트 리스트 (순서대로 인덱싱)
    all_charts = []
    all_findings_map = {}
    chart_offset = 0

    # 엔티티별 섹션
    for i, entry in enumerate(entries_data):
        name = entry['name']
        card_html = entry.get('card_html', '')
        findings = entry.get('findings', [])
        charts = entry.get('charts', [])

        all_findings_map[name] = findings

        section_html, charts_used = _build_entity_section(
            i, name, card_html, findings, charts, chart_offset
        )
        parts.append(section_html)
        all_charts.extend(charts)
        chart_offset += len(charts)

    # 결론
    conclusion = build_conclusion(key, entity_names, all_findings_map)
    if conclusion:
        parts.append(conclusion)

    # 마무리
    parts.append('<hr style="margin:2rem 0 1rem;">')
    parts.append(
        f'<p style="color:#adb5bd;font-size:0.85rem;">'
        f'본 분석은 glvpen Python 배치 분석 시스템으로 자동 생성되었습니다.<br>'
        f'분석 일시: {datetime.now().strftime("%Y년 %m월 %d일")}</p>'
    )

    article_html = '\n'.join(parts)
    return article_html, all_charts, None


# ==================== 메인 생성 함수 ====================

def generate_article(key: str, entries_data: list, meta: dict,
                     data_season: int = None,
                     predict_season: int = None) -> tuple:
    """
    기사형 HTML과 통합 차트 리스트를 생성.

    entries_data: [
        {
            'name': '김도영',
            'card_html': '<div>...</div>',
            'findings': [...],
            'charts': [...],
        },
        ...
    ]

    반환: (article_html, all_charts_list, summary_or_none)
    - summary: Gemini가 생성한 요약 (없으면 None)
    """
    # 전체 차트 리스트 수집
    all_charts = []
    for entry in entries_data:
        all_charts.extend(entry.get('charts', []))

    # 1) Gemini 시도
    try:
        prompt = _build_gemini_prompt(key, entries_data, meta,
                                      data_season, predict_season)
        print("    [Gemini] AI 기사 생성 요청 중...")
        ai_text = gemini_client.call(prompt)

        if ai_text and _validate_ai_response(ai_text):
            article_html, summary = _parse_ai_response(ai_text, entries_data)

            # 마무리 푸터 추가
            article_html += (
                '\n<hr style="margin:2rem 0 1rem;">'
                f'\n<p style="color:#adb5bd;font-size:0.85rem;">'
                f'본 분석은 glvpen AI 분석 시스템(Gemini)으로 자동 생성되었습니다.<br>'
                f'분석 일시: {datetime.now().strftime("%Y년 %m월 %d일")}</p>'
            )
            print("    [Gemini] AI 기사 생성 성공")
            return article_html, all_charts, summary
        else:
            print("    [Gemini] 응답 검증 실패 — fallback 사용")
    except Exception as e:
        print(f"    [Gemini] 오류 발생: {e} — fallback 사용")

    # 2) Fallback: 기존 로직
    return _generate_fallback(key, entries_data, meta,
                              data_season, predict_season)
