package com.kepg.glvpen.common.stats;

/**
 * 세이버메트릭스 지표 계산기
 * 기본 지표(H, BB, SO, AB, PA, IP, HR, ER 등)로부터 파생 지표를 산출한다.
 */
public class SabermetricsCalculator {

    private SabermetricsCalculator() {}

    /**
     * BABIP (Batting Average on Balls In Play)
     * = (H - HR) / (AB - SO - HR + SF)
     * SF(희생플라이)가 없으면 0으로 처리
     */
    public static double calcBABIP(double h, double hr, double ab, double so, double sf) {
        double denom = ab - so - hr + sf;
        if (denom <= 0) return 0.0;
        return (h - hr) / denom;
    }

    /**
     * ISO (Isolated Power) = SLG - AVG
     */
    public static double calcISO(double slg, double avg) {
        return slg - avg;
    }

    /**
     * K% (삼진율) = SO / PA
     */
    public static double calcKRate(double so, double pa) {
        if (pa <= 0) return 0.0;
        return so / pa;
    }

    /**
     * BB% (볼넷율) = BB / PA
     */
    public static double calcBBRate(double bb, double pa) {
        if (pa <= 0) return 0.0;
        return bb / pa;
    }

    /**
     * K/9 (9이닝당 삼진) = (SO / IP) * 9
     */
    public static double calcK9(double so, double ip) {
        if (ip <= 0) return 0.0;
        return (so / ip) * 9.0;
    }

    /**
     * BB/9 (9이닝당 볼넷) = (BB / IP) * 9
     */
    public static double calcBB9(double bb, double ip) {
        if (ip <= 0) return 0.0;
        return (bb / ip) * 9.0;
    }

    /**
     * HR/9 (9이닝당 피홈런) = (HR / IP) * 9
     */
    public static double calcHR9(double hr, double ip) {
        if (ip <= 0) return 0.0;
        return (hr / ip) * 9.0;
    }

    // ==================== 기본 산출 지표 (KBO 사이트 BasicOld 전환 대응) ====================

    /**
     * TB (Total Bases) = H + 2B + 2×3B + 3×HR
     * BasicOld.aspx에서 TB가 직접 제공되지 않으므로 계산
     */
    public static double calcTB(double h, double doubles, double triples, double hr) {
        return h + doubles + 2 * triples + 3 * hr;
    }

    /**
     * SLG (Slugging Percentage) = TB / AB
     */
    public static double calcSLG(double tb, double ab) {
        if (ab <= 0) return 0.0;
        return tb / ab;
    }

    /**
     * OBP (On-Base Percentage) = (H + BB + HBP) / (AB + BB + HBP + SF)
     * SF가 없으면 0으로 처리 (BasicOld에서 SF 미제공)
     */
    public static double calcOBP(double h, double bb, double hbp, double ab, double sf) {
        double denom = ab + bb + hbp + sf;
        if (denom <= 0) return 0.0;
        return (h + bb + hbp) / denom;
    }

    /**
     * OPS (On-base Plus Slugging) = OBP + SLG
     */
    public static double calcOPS(double obp, double slg) {
        return obp + slg;
    }

    // ==================== 타자 추가 지표 ====================

    /**
     * wOBA (Weighted On-Base Average) — FanGraphs 기본 가중치 사용
     * = (0.69*BB + 0.72*HBP + 0.89*1B + 1.27*2B + 1.62*3B + 2.10*HR) / (AB + BB + SF + HBP)
     */
    public static double calcWOBA(double bb, double hbp, double singles, double doubles,
                                   double triples, double hr, double ab, double sf) {
        double denom = ab + bb + sf + hbp;
        if (denom <= 0) return 0.0;
        return (0.69 * bb + 0.72 * hbp + 0.89 * singles + 1.27 * doubles
                + 1.62 * triples + 2.10 * hr) / denom;
    }

    /**
     * AB/HR (타수 대비 홈런 빈도 — 낮을수록 장타력 우수)
     */
    public static double calcABperHR(double ab, double hr) {
        if (hr <= 0) return 0.0;
        return ab / hr;
    }

    /**
     * PA/K (타석당 삼진 빈도 — 높을수록 컨택 능력 우수)
     */
    public static double calcPAperK(double pa, double so) {
        if (so <= 0) return 0.0;
        return pa / so;
    }

    // ==================== 투수 추가 지표 ====================

    /**
     * FIP (Fielding Independent Pitching)
     * = ((13 × HR + 3 × (BB + HBP) - 2 × SO) / IP) + cFIP
     * cFIP ≈ 3.2 (KBO 리그 상수)
     */
    public static double calcFIP(double hr, double bb, double hbp, double so, double ip) {
        if (ip <= 0) return 0.0;
        return ((13 * hr + 3 * (bb + hbp) - 2 * so) / ip) + 3.2;
    }

    /**
     * LOB% (잔루율 근사) — 높을수록 위기 관리 우수
     * ≈ (H + BB + HBP - R) / (H + BB + HBP - 1.4 * HR)
     */
    public static double calcLOBPercent(double h, double bb, double hbp, double r, double hr) {
        double denom = h + bb + hbp - 1.4 * hr;
        if (denom <= 0) return 0.0;
        double lob = (h + bb + hbp - r) / denom;
        return Math.max(0.0, Math.min(1.0, lob));
    }
}
