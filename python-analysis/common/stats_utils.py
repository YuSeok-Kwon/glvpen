"""
통계 분석 공통 유틸.
기술통계 계산 + 가설검정(scipy) 래퍼 함수 모음.
모든 결과는 JSON 직렬화 가능한 딕셔너리로 반환.
"""
import numpy as np
from scipy import stats
from typing import Dict, List, Optional


class StatsUtils:
    """통계 분석 유틸리티"""

    # ==================== 기술통계 ====================

    @staticmethod
    def descriptive(data: np.ndarray, label: str = '') -> Dict:
        """기술통계량 계산 (평균, 중앙값, 표준편차, 사분위수 등)"""
        clean = data[~np.isnan(data)]
        if len(clean) == 0:
            return {'label': label, 'n': 0, 'mean': 0, 'median': 0,
                    'std': 0, 'min': 0, 'max': 0, 'q1': 0, 'q3': 0}
        return {
            'label': label,
            'n': int(len(clean)),
            'mean': round(float(np.mean(clean)), 4),
            'median': round(float(np.median(clean)), 4),
            'std': round(float(np.std(clean, ddof=1)), 4) if len(clean) > 1 else 0.0,
            'min': round(float(np.min(clean)), 4),
            'max': round(float(np.max(clean)), 4),
            'q1': round(float(np.percentile(clean, 25)), 4),
            'q3': round(float(np.percentile(clean, 75)), 4),
        }

    # ==================== 정규성 검정 ====================

    @staticmethod
    def normality_test(data: np.ndarray, label: str = '') -> Dict:
        """Shapiro-Wilk 정규성 검정 (n < 5000일 때 적합)"""
        clean = data[~np.isnan(data)]
        if len(clean) < 3:
            return {'test': 'Shapiro-Wilk', 'label': label,
                    'p_value': 1.0, 'is_normal': True, 'note': '표본 부족'}

        # Shapiro-Wilk는 최대 5000개 표본까지 지원
        sample = clean[:5000] if len(clean) > 5000 else clean
        stat, p = stats.shapiro(sample)
        return {
            'test': 'Shapiro-Wilk',
            'label': label,
            'statistic': round(float(stat), 4),
            'p_value': round(float(p), 4),
            'is_normal': p > 0.05,
            'interpretation': f"{'정규분포 따름' if p > 0.05 else '정규분포 따르지 않음'} (p={p:.4f})",
        }

    # ==================== t-검정 ====================

    @staticmethod
    def t_test_ind(group1: np.ndarray, group2: np.ndarray,
                   label1: str = '그룹1', label2: str = '그룹2') -> Dict:
        """독립표본 t-검정 (두 그룹의 평균 비교)"""
        g1 = group1[~np.isnan(group1)]
        g2 = group2[~np.isnan(group2)]
        if len(g1) < 2 or len(g2) < 2:
            return {'test': '독립표본 t-검정', 'note': '표본 부족',
                    'p_value': 1.0, 'significant': False}

        stat, p = stats.ttest_ind(g1, g2, equal_var=False)  # Welch's t-test
        return {
            'test': '독립표본 t-검정 (Welch)',
            'groups': f'{label1} vs {label2}',
            'mean_diff': round(float(np.mean(g1) - np.mean(g2)), 4),
            'group1_mean': round(float(np.mean(g1)), 4),
            'group2_mean': round(float(np.mean(g2)), 4),
            't_statistic': round(float(stat), 4),
            'p_value': round(float(p), 4),
            'significant': p < 0.05,
            'interpretation': f"{'유의미한 차이 있음' if p < 0.05 else '유의미한 차이 없음'} (p={p:.4f})",
        }

    @staticmethod
    def t_test_paired(before: np.ndarray, after: np.ndarray,
                      label: str = '전후 비교') -> Dict:
        """쌍표본 t-검정 (동일 대상의 전후 비교)"""
        # NaN 제거 (둘 다 유효한 쌍만)
        mask = ~np.isnan(before) & ~np.isnan(after)
        b, a = before[mask], after[mask]
        if len(b) < 2:
            return {'test': '쌍표본 t-검정', 'label': label,
                    'note': '표본 부족', 'p_value': 1.0, 'significant': False}

        stat, p = stats.ttest_rel(b, a)
        return {
            'test': '쌍표본 t-검정',
            'label': label,
            'n_pairs': int(len(b)),
            'before_mean': round(float(np.mean(b)), 4),
            'after_mean': round(float(np.mean(a)), 4),
            'mean_diff': round(float(np.mean(a) - np.mean(b)), 4),
            't_statistic': round(float(stat), 4),
            'p_value': round(float(p), 4),
            'significant': p < 0.05,
            'interpretation': f"{'유의미한 변화' if p < 0.05 else '유의미한 변화 없음'} (p={p:.4f})",
        }

    # ==================== ANOVA ====================

    @staticmethod
    def anova(groups: Dict[str, np.ndarray]) -> Dict:
        """일원배치 분산분석 (ANOVA) - 3개 이상 그룹 비교"""
        clean_groups = {}
        for name, data in groups.items():
            clean = data[~np.isnan(data)]
            if len(clean) >= 2:
                clean_groups[name] = clean

        if len(clean_groups) < 2:
            return {'test': 'One-way ANOVA', 'note': '유효 그룹 부족',
                    'p_value': 1.0, 'significant': False}

        f_stat, p = stats.f_oneway(*clean_groups.values())
        group_means = {name: round(float(np.mean(data)), 4)
                       for name, data in clean_groups.items()}

        return {
            'test': 'One-way ANOVA',
            'n_groups': len(clean_groups),
            'group_means': group_means,
            'f_statistic': round(float(f_stat), 4),
            'p_value': round(float(p), 4),
            'significant': p < 0.05,
            'interpretation': f"{'그룹 간 유의미한 차이 있음' if p < 0.05 else '그룹 간 유의미한 차이 없음'} (F={f_stat:.2f}, p={p:.4f})",
        }

    # ==================== 상관분석 ====================

    @staticmethod
    def correlation(x: np.ndarray, y: np.ndarray,
                    x_label: str = 'X', y_label: str = 'Y') -> Dict:
        """Pearson 상관계수 (두 연속 변수 간 선형 관계)"""
        mask = ~np.isnan(x) & ~np.isnan(y)
        x_clean, y_clean = x[mask], y[mask]
        if len(x_clean) < 3:
            return {'test': 'Pearson 상관분석', 'note': '표본 부족',
                    'correlation': 0.0, 'p_value': 1.0, 'significant': False}

        corr, p = stats.pearsonr(x_clean, y_clean)

        # 상관 강도 해석
        abs_corr = abs(corr)
        if abs_corr >= 0.7:
            strength = '강한'
        elif abs_corr >= 0.4:
            strength = '보통'
        elif abs_corr >= 0.2:
            strength = '약한'
        else:
            strength = '거의 없는'

        direction = '양의' if corr > 0 else '음의'

        return {
            'test': 'Pearson 상관분석',
            'variables': f'{x_label} vs {y_label}',
            'n': int(len(x_clean)),
            'correlation': round(float(corr), 4),
            'p_value': round(float(p), 4),
            'significant': p < 0.05,
            'interpretation': f"{strength} {direction} 상관관계 (r={corr:.4f}, p={p:.4f})",
        }

    # ==================== 카이제곱 검정 ====================

    @staticmethod
    def chi_square(observed: np.ndarray, label: str = '') -> Dict:
        """카이제곱 독립성 검정 (관측 빈도 분할표)"""
        try:
            chi2, p, dof, expected = stats.chi2_contingency(observed)
            return {
                'test': '카이제곱 독립성 검정',
                'label': label,
                'chi2_statistic': round(float(chi2), 4),
                'degrees_of_freedom': int(dof),
                'p_value': round(float(p), 4),
                'significant': p < 0.05,
                'interpretation': f"{'독립이 아님 (유의미한 연관)' if p < 0.05 else '독립 (유의미한 연관 없음)'} (chi2={chi2:.2f}, p={p:.4f})",
            }
        except Exception as e:
            return {'test': '카이제곱 검정', 'label': label,
                    'note': f'검정 실패: {str(e)}', 'p_value': 1.0, 'significant': False}

    # ==================== 인사이트 포맷 ====================

    @staticmethod
    def format_insight(title: str, findings: List[str]) -> str:
        """분석 인사이트를 Gemini 프롬프트용 텍스트로 포맷"""
        lines = [f"[{title}]"]
        for f in findings:
            lines.append(f"- {f}")
        return '\n'.join(lines)
