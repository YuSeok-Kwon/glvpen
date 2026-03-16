"""
연도별/월별 비교 분석 프레임워크.
각 토픽의 analyze() 결과를 기준 앵커(최신 연도/월)와 비교하여
추가 기사용 분석 결과를 생성.
"""
import json
import numpy as np
from typing import Callable, Dict, List, Tuple, Optional
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


class ComparisonAnalyzer:
    """연도/월 비교 분석 실행기"""

    def __init__(self, db: DBConnector):
        self.db = db

    def run_yearly_comparisons(self, topic: int, sub_topic: str,
                                analyze_fn: Callable,
                                anchor_season: int,
                                min_seasons: int = 2) -> List[Dict]:
        """
        연도별 비교 분석 실행.
        anchor_season(최신)을 기준으로 과거 시즌들과 비교.

        반환: [{'season': 2025, 'comparison_type': 'yearly', 'anchor_value': '2025',
                'compare_with': 2023, 'stats_json': ..., 'chart_json': ..., ...}, ...]
        """
        available = self.db.get_available_seasons()
        if len(available) < min_seasons:
            return []

        # 앵커 = 최신 시즌
        anchor = anchor_season if anchor_season in available else available[0]
        compare_targets = [s for s in available if s != anchor]

        results = []
        for target_season in compare_targets[:5]:  # 최대 5개 과거 시즌과 비교
            try:
                result = self._compare_yearly(
                    topic, sub_topic, analyze_fn, anchor, target_season
                )
                if result:
                    results.append(result)
            except Exception as e:
                print(f"    연도 비교 실패 ({anchor} vs {target_season}): {e}")

        return results

    def run_monthly_comparisons(self, topic: int, sub_topic: str,
                                 season: int,
                                 monthly_analyze_fn: Callable) -> List[Dict]:
        """
        월별 비교 분석 실행.
        가장 최신 월을 앵커로, 이전 월들과 비교.

        반환: [{'season': 2025, 'comparison_type': 'monthly', 'anchor_value': '9',
                'compare_with': 6, ...}, ...]
        """
        available_months = self.db.get_available_months(season)
        if len(available_months) < 2:
            return []

        # 앵커 = 가장 마지막 월
        anchor_month = available_months[-1]
        compare_months = available_months[:-1]

        results = []
        for target_month in compare_months:
            try:
                result = self._compare_monthly(
                    topic, sub_topic, monthly_analyze_fn,
                    season, anchor_month, target_month
                )
                if result:
                    results.append(result)
            except Exception as e:
                print(f"    월별 비교 실패 ({anchor_month}월 vs {target_month}월): {e}")

        return results

    def _compare_yearly(self, topic: int, sub_topic: str,
                        analyze_fn: Callable,
                        anchor_season: int, target_season: int) -> Optional[Dict]:
        """두 시즌 간 비교 분석 수행"""
        # 앵커 시즌 분석
        anchor_result = analyze_fn(anchor_season, self.db)
        target_result = analyze_fn(target_season, self.db)

        if not anchor_result or not target_result:
            return None

        a_stats, a_charts, a_insight, a_hypo = anchor_result
        t_stats, t_charts, t_insight, t_hypo = target_result

        # 비교 인사이트 생성
        comparison_insight = self._build_comparison_insight(
            f"{anchor_season} vs {target_season}", a_stats, t_stats,
            anchor_label=str(anchor_season), target_label=str(target_season)
        )

        # 비교 차트 생성
        comparison_charts = self._build_comparison_charts(
            a_stats, t_stats, str(anchor_season), str(target_season)
        )

        # 앵커 차트 + 비교 차트 합치기
        try:
            all_charts = json.loads(a_charts) if isinstance(a_charts, str) else a_charts
        except:
            all_charts = []
        all_charts.extend(comparison_charts)

        return {
            'topic': topic,
            'sub_topic': sub_topic,
            'season': anchor_season,
            'comparison_type': 'yearly',
            'anchor_value': f'{anchor_season}_vs_{target_season}',
            'compare_with': target_season,
            'stats_json': a_stats,
            'chart_json': json.dumps(all_charts, ensure_ascii=False),
            'insight_text': f"{a_insight}\n\n{comparison_insight}",
            'hypothesis_json': a_hypo,
        }

    def _compare_monthly(self, topic: int, sub_topic: str,
                         monthly_fn: Callable,
                         season: int, anchor_month: int, target_month: int) -> Optional[Dict]:
        """두 월 간 비교 분석 수행"""
        anchor_result = monthly_fn(season, anchor_month, self.db)
        target_result = monthly_fn(season, target_month, self.db)

        if not anchor_result or not target_result:
            return None

        a_stats, a_charts, a_insight, a_hypo = anchor_result
        t_stats, t_charts, t_insight, t_hypo = target_result

        comparison_insight = self._build_comparison_insight(
            f"{anchor_month}월 vs {target_month}월", a_stats, t_stats,
            anchor_label=f"{anchor_month}월", target_label=f"{target_month}월"
        )

        comparison_charts = self._build_comparison_charts(
            a_stats, t_stats, f"{anchor_month}월", f"{target_month}월"
        )

        try:
            all_charts = json.loads(a_charts) if isinstance(a_charts, str) else a_charts
        except:
            all_charts = []
        all_charts.extend(comparison_charts)

        return {
            'topic': topic,
            'sub_topic': sub_topic,
            'season': season,
            'comparison_type': 'monthly',
            'anchor_value': f'{anchor_month}_vs_{target_month}',
            'compare_with': target_month,
            'stats_json': a_stats,
            'chart_json': json.dumps(all_charts, ensure_ascii=False),
            'insight_text': f"{a_insight}\n\n{comparison_insight}",
            'hypothesis_json': a_hypo,
        }

    def _build_comparison_insight(self, title: str, anchor_stats: str,
                                   target_stats: str, anchor_label: str,
                                   target_label: str) -> str:
        """두 분석 결과의 통계치 비교 인사이트 생성"""
        findings = [f"[비교 분석: {title}]"]

        try:
            a = json.loads(anchor_stats) if isinstance(anchor_stats, str) else anchor_stats
            t = json.loads(target_stats) if isinstance(target_stats, str) else target_stats
        except:
            findings.append("- 비교 데이터 파싱 실패")
            return '\n'.join(findings)

        # 공통 키에서 mean 비교
        common_keys = set(a.keys()) & set(t.keys())
        for key in sorted(common_keys):
            a_val = a[key]
            t_val = t[key]
            if isinstance(a_val, dict) and isinstance(t_val, dict):
                if 'mean' in a_val and 'mean' in t_val:
                    diff = a_val['mean'] - t_val['mean']
                    direction = '상승' if diff > 0 else '하락' if diff < 0 else '동일'
                    findings.append(
                        f"- {key}: {anchor_label} {a_val['mean']:.4f} vs "
                        f"{target_label} {t_val['mean']:.4f} ({direction}, "
                        f"차이: {abs(diff):.4f})"
                    )

        if len(findings) == 1:
            findings.append("- 비교 가능한 수치 지표 없음")

        return '\n'.join(findings)

    def _build_comparison_charts(self, anchor_stats: str, target_stats: str,
                                  anchor_label: str, target_label: str) -> List[Dict]:
        """비교 차트 자동 생성"""
        charts = []
        try:
            a = json.loads(anchor_stats) if isinstance(anchor_stats, str) else anchor_stats
            t = json.loads(target_stats) if isinstance(target_stats, str) else target_stats
        except:
            return charts

        # mean 값이 있는 지표들 수집
        labels = []
        anchor_data = []
        target_data = []

        common_keys = sorted(set(a.keys()) & set(t.keys()))
        for key in common_keys:
            a_val = a[key]
            t_val = t[key]
            if isinstance(a_val, dict) and isinstance(t_val, dict):
                if 'mean' in a_val and 'mean' in t_val:
                    labels.append(key)
                    anchor_data.append(round(a_val['mean'], 4))
                    target_data.append(round(t_val['mean'], 4))

        if labels:
            charts.append(ChartBuilder.bar(
                f'{anchor_label} vs {target_label} 주요 지표 비교',
                labels,
                [
                    {'label': anchor_label, 'data': anchor_data},
                    {'label': target_label, 'data': target_data},
                ]
            ))

        return charts
