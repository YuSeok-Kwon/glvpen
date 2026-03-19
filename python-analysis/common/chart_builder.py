"""
Chart.js 4.4.1 호환 JSON 생성 유틸.
column-detail.html에서 다중 차트 배열로 렌더링되므로
모든 메서드는 단일 차트 딕셔너리를 반환.

팀/선수 색상: dataset label에 팀명이 포함되면 해당 구단 공식 색상 자동 적용.
"""
from typing import List, Dict, Any, Optional
from config import CHART_COLORS, BASELINE_COLOR, get_team_color


def _resolve_color(label: str, index: int, explicit_color: str = None) -> str:
    """label에서 팀 색상 매칭, 없으면 팔레트 순환"""
    if explicit_color:
        return explicit_color
    if label and label != '리그 평균':
        team_color = get_team_color(label, fallback_idx=index)
        return team_color
    if label == '리그 평균':
        return BASELINE_COLOR
    return CHART_COLORS[index % len(CHART_COLORS)]


class ChartBuilder:
    """Chart.js 호환 차트 데이터 생성기"""

    @staticmethod
    def bar(title: str, labels: List[str], datasets: List[Dict[str, Any]]) -> Dict:
        """막대 차트"""
        for i, ds in enumerate(datasets):
            if 'backgroundColor' not in ds:
                ds['backgroundColor'] = _resolve_color(ds.get('label', ''), i)
        # 단일 dataset + 여러 label → per-bar 색상 배열로 교체
        if len(datasets) == 1 and isinstance(datasets[0].get('backgroundColor'), str):
            ds = datasets[0]
            ds['backgroundColor'] = [
                _resolve_color(lbl, i) for i, lbl in enumerate(labels)
            ]
        return {
            'chartType': 'bar',
            'title': title,
            'labels': labels,
            'datasets': datasets,
        }

    @staticmethod
    def line(title: str, labels: List[str], datasets: List[Dict[str, Any]]) -> Dict:
        """선형 차트"""
        for i, ds in enumerate(datasets):
            if 'borderColor' not in ds:
                ds['borderColor'] = _resolve_color(ds.get('label', ''), i)
            ds.setdefault('fill', False)
            ds.setdefault('tension', 0.4)
            ds.setdefault('borderWidth', 2.5)
            ds.setdefault('pointRadius', 4)
            ds.setdefault('pointHoverRadius', 6)
        return {
            'chartType': 'line',
            'title': title,
            'labels': labels,
            'datasets': datasets,
        }

    @staticmethod
    def radar(title: str, labels: List[str], datasets: List[Dict[str, Any]]) -> Dict:
        """레이더 차트"""
        for i, ds in enumerate(datasets):
            color = _resolve_color(ds.get('label', ''), i)
            ds.setdefault('borderColor', color)
            ds.setdefault('backgroundColor', color + '28')  # 16% 투명도
            ds.setdefault('borderWidth', 2.5)
            ds.setdefault('pointRadius', 3)
        return {
            'chartType': 'radar',
            'title': title,
            'labels': labels,
            'datasets': datasets,
        }

    @staticmethod
    def doughnut(title: str, labels: List[str], data: List[float],
                 colors: List[str] = None) -> Dict:
        """도넛 차트"""
        if not colors:
            colors = [_resolve_color(lbl, i) for i, lbl in enumerate(labels)]
        return {
            'chartType': 'doughnut',
            'title': title,
            'labels': labels,
            'datasets': [{
                'data': data,
                'backgroundColor': colors,
                'borderWidth': 2,
                'borderColor': '#ffffff',
            }],
        }

    @staticmethod
    def stacked_bar(title: str, labels: List[str],
                    datasets: List[Dict[str, Any]]) -> Dict:
        """누적 막대 차트"""
        for i, ds in enumerate(datasets):
            if 'backgroundColor' not in ds:
                ds['backgroundColor'] = _resolve_color(ds.get('label', ''), i)
        return {
            'chartType': 'bar',
            'title': title,
            'labels': labels,
            'datasets': datasets,
            'options': {
                'scales': {
                    'x': {'stacked': True},
                    'y': {'stacked': True},
                },
            },
        }

    @staticmethod
    def scatter(title: str, datasets: List[Dict[str, Any]]) -> Dict:
        """산점도 차트"""
        for i, ds in enumerate(datasets):
            # (0,0) 포인트 제거
            if 'data' in ds:
                ds['data'] = [p for p in ds['data']
                              if p.get('x', 0) != 0 or p.get('y', 0) != 0]
            if 'backgroundColor' not in ds:
                ds['backgroundColor'] = _resolve_color(ds.get('label', ''), i)
            ds.setdefault('pointRadius', 5)
            ds.setdefault('pointHoverRadius', 7)
        return {
            'chartType': 'scatter',
            'title': title,
            'datasets': datasets,
        }
