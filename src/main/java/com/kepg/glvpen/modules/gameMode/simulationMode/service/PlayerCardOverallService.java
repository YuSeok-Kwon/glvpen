package com.kepg.glvpen.modules.gameMode.simulationMode.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.player.repository.PlayerRepository;
import com.kepg.glvpen.modules.player.stats.domain.BatterStats;
import com.kepg.glvpen.modules.player.stats.domain.PitcherStats;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.PlayerCardOverallDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.domain.PlayerCardOverall;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.RawBatterScore;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.RawPitcherScore;
import com.kepg.glvpen.modules.gameMode.simulationMode.repository.PlayerCardOverallRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PlayerCardOverallService {
	
    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final PlayerCardOverallRepository playerCardOverallRepository;
    private final PlayerRepository playerRepository;
    
    // 타자 오버롤 계산 (포지션 + 시즌 리스트)
    public void calculateBatterOverallByPosition(List<Integer> seasons, String targetPosition) {

        List<RawBatterScore> rawList = collectFilteredRawScores(seasons, targetPosition);

        if (rawList.isEmpty()) return;

        Map<String, Double> minMap = new HashMap<>();
        Map<String, Double> maxMap = new HashMap<>();
        initMinMaxMaps(rawList, minMap, maxMap);

        for (RawBatterScore raw : rawList) {
            PlayerCardOverall entity = PlayerCardOverall.builder()
                .playerId(raw.getPlayerId())
                .season(raw.getSeason())
                .type("BATTER")
                .power(scaleToRange(raw.getPower(), minMap.get("power"), maxMap.get("power")))
                .contact(scaleToRange(raw.getContact(), minMap.get("contact"), maxMap.get("contact")))
                .discipline(scaleToRange(raw.getDiscipline(), minMap.get("discipline"), maxMap.get("discipline")))
                .speed(scaleToRange(raw.getSpeed(), minMap.get("speed"), maxMap.get("speed")))
                .control(0)
                .stuff(0)
                .stamina(0)
                .overall(scaleToRange(raw.getOverall(), minMap.get("overall"), maxMap.get("overall")))
                .build();

            playerCardOverallRepository.deleteByPlayerIdAndSeason(raw.getPlayerId(), raw.getSeason());
            playerCardOverallRepository.saveAndFlush(entity);
        }
    }
    
    // 타자 raw 데이터 필터링 (포지션, 시즌별로 조건 통과한 선수만)
    private List<RawBatterScore> collectFilteredRawScores(List<Integer> seasons, String targetPosition) {
        List<RawBatterScore> rawList = new ArrayList<>();

        for (int season : seasons) {
            List<BatterStats> allStats = batterStatsRepository.findBySeason(season);
            Map<Integer, Map<String, Double>> statMapByPlayer = new HashMap<>();
            Map<Integer, String> positionMap = new HashMap<>();

            for (BatterStats stat : allStats) {
                Integer playerId = stat.getPlayerId();
                statMapByPlayer.putIfAbsent(playerId, new HashMap<>());
                statMapByPlayer.get(playerId).put(stat.getCategory(), stat.getValue());
                positionMap.putIfAbsent(playerId, stat.getPosition());
            }

            for (Map.Entry<Integer, Map<String, Double>> entry : statMapByPlayer.entrySet()) {
                Integer playerId = entry.getKey();
                String position = positionMap.get(playerId);
                if (!targetPosition.equals(position)) continue;

                Map<String, Double> statMap = entry.getValue();
                double g = statMap.getOrDefault("G", 0.0);
                double pa = statMap.getOrDefault("PA", 0.0);
                if (g < 10 || pa < 20) continue;

                // 타격 기록 없는 선수 제외 (AVG=0, H=0)
                double avg = statMap.getOrDefault("AVG", 0.0);
                double h = statMap.getOrDefault("H", 0.0);
                if (avg == 0 && h == 0) continue;

                RawBatterScore raw = calculateRawBatterScoreWithWeight(playerId, season, statMap);
                raw.setSeason(season);
                rawList.add(raw);
            }
        }

        return rawList;
    }
    
    // 타자 점수 계산 (보정 포함)
    private RawBatterScore calculateRawBatterScoreWithWeight(Integer playerId, int season, Map<String, Double> statMap) {
        // wOBA 기반 가중치 (WAR 데이터 없음 대응)
        double woba = statMap.getOrDefault("wOBA", 0.0);
        double weight = woba > 0 ? 1.0 + Math.pow(woba / 0.35, 1.5) / 4.0 : 1.0;

        double pa = statMap.getOrDefault("PA", 0.0);
        double gameRatio = Math.min(pa / 400.0, 1.5);

        double hrScore = Math.min(statMap.getOrDefault("HR", 0.0) / 50.0, 1.5) * 100;
        double slgScore = Math.min(statMap.getOrDefault("SLG", 0.0) / 0.6, 1.5) * 100;
        double power = (hrScore * 0.6 + slgScore * 0.4) * weight * gameRatio;

        double hScore = Math.min(statMap.getOrDefault("H", 0.0) / 180.0, 1.5) * 100;
        double avgScore = Math.min(statMap.getOrDefault("AVG", 0.0) / 0.35, 1.5) * 100;
        double contact = (hScore * 0.5 + avgScore * 0.5) * weight * gameRatio;

        // SO=0 과대평가 방지: PA < 100이면 중립값
        double so = statMap.getOrDefault("SO", 0.0);
        double bbScore = Math.min(statMap.getOrDefault("BB", 0.0) / 80.0, 1.5) * 100;
        double soScore;
        if (so == 0 && pa < 100) {
            soScore = 50;
        } else {
            soScore = (1.0 - Math.min(so / 150.0, 1.0)) * 100;
        }
        double discipline = (bbScore * 0.5 + soScore * 0.5) * weight * gameRatio;

        double sbScore = Math.min(statMap.getOrDefault("SB", 0.0) / 50.0, 1.5) * 100;
        double speed = sbScore * weight * gameRatio;

        double overall = power * 0.3 + contact * 0.3 + discipline * 0.2 + speed * 0.2;

        return RawBatterScore.builder()
            .playerId(playerId)
            .power(power)
            .contact(contact)
            .discipline(discipline)
            .speed(speed)
            .overall(overall)
            .build();
    }
    
    // 정규화용 최대값: 상위 4% 평균값 계산
    private double getTopPercentAverage(List<Double> values, double topPercent) {
        int count = Math.max(1, (int) (values.size() * topPercent));
        return values.stream()
            .sorted(Comparator.reverseOrder())  // 내림차순
            .limit(count)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(1.0);  // fallback
    }
    
    // 정규화용 min/max 계산
    private void initMinMaxMaps(List<RawBatterScore> rawList, Map<String, Double> minMap, Map<String, Double> maxMap) {

        List<Double> powers = new ArrayList<>();
        List<Double> contacts = new ArrayList<>();
        List<Double> disciplines = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();
        List<Double> overalls = new ArrayList<>();

        for (RawBatterScore raw : rawList) {
            powers.add(raw.getPower());
            contacts.add(raw.getContact());
            disciplines.add(raw.getDiscipline());
            speeds.add(raw.getSpeed());
            overalls.add(raw.getOverall());
        }

        minMap.put("power", Collections.min(powers));
        maxMap.put("power", getTopPercentAverage(powers, 0.04));

        minMap.put("contact", Collections.min(contacts));
        maxMap.put("contact", getTopPercentAverage(contacts, 0.04));

        minMap.put("discipline", Collections.min(disciplines));
        maxMap.put("discipline", getTopPercentAverage(disciplines, 0.04));

        minMap.put("speed", Collections.min(speeds));
        maxMap.put("speed", getTopPercentAverage(speeds, 0.04));

        minMap.put("overall", Collections.min(overalls));
        maxMap.put("overall", getTopPercentAverage(overalls, 0.04));
    }

    // 점수 정규화 (10~100, 범위 강제)
    private int scaleToRange(double value, double min, double max) {
        if (max == min) return 10;
        int result = 10 + (int) Math.round((value - min) / (max - min) * 90);
        return Math.max(10, Math.min(100, result));
    }
    
    // 투수 오버롤 계산 (선발/구원 포함)
    @Transactional
    public void calculatePitcherOverall(List<Integer> seasons) {

        List<RawPitcherScore> rawList = new ArrayList<>();

        for (int season : seasons) {
            List<PitcherStats> allStats = pitcherStatsRepository.findBySeason(season);

            Map<Integer, Map<String, Double>> statMapByPlayer = new HashMap<>();

            for (PitcherStats stat : allStats) {
                Integer playerId = stat.getPlayerId();
                statMapByPlayer.putIfAbsent(playerId, new HashMap<>());
                statMapByPlayer.get(playerId).put(stat.getCategory(), stat.getValue());
            }

            for (Map.Entry<Integer, Map<String, Double>> entry : statMapByPlayer.entrySet()) {
                Integer playerId = entry.getKey();
                Map<String, Double> statMap = entry.getValue();

                double ip = statMap.getOrDefault("IP", 0.0);
                double gs = statMap.getOrDefault("GS", 0.0);
                double gr = statMap.getOrDefault("GR", 0.0);

                if ((gs + gr) < 20 && ip < 30.0) continue;

                RawPitcherScore raw = calculateRawPitcherScoreWithMinMax(playerId, season, statMap);
                raw.setSeason(season);
                rawList.add(raw);
            }
        }

        // 최고, 최저값 계산 + 상위 4% 평균을 max로
        Map<String, Double> minMap = new HashMap<>();
        Map<String, Double> maxMap = new HashMap<>();
        initPitcherMinMaxMaps(rawList, minMap, maxMap);

        // 정규화 및 저장
        for (RawPitcherScore raw : rawList) {
            int control = scaleToRange(raw.getControl(), minMap.get("control"), maxMap.get("control"));
            int stuff = scaleToRange(raw.getStuff(), minMap.get("stuff"), maxMap.get("stuff"));
            int stamina = scaleToRange(raw.getStamina(), minMap.get("stamina"), maxMap.get("stamina"));
            int overall = scaleToRange(raw.getOverall(), minMap.get("overall"), maxMap.get("overall"));

            PlayerCardOverall entity = PlayerCardOverall.builder()
                .playerId(raw.getPlayerId())
                .season(raw.getSeason())
                .type("PITCHER")
                .power(0)
                .contact(0)
                .discipline(0)
                .speed(0)
                .control(control)
                .stuff(stuff)
                .stamina(stamina)
                .overall(overall)
                .build();

            playerCardOverallRepository.deleteByPlayerIdAndSeason(raw.getPlayerId(), raw.getSeason());
            playerCardOverallRepository.saveAndFlush(entity);
        }
    }
    
 // 투수 실제 데이터 추출 -> 보정
    private RawPitcherScore calculateRawPitcherScoreWithMinMax(int playerId, int season, Map<String, Double> statMap) {
        // FIP 기반 가중치 (WAR 데이터 없음 대응)
        double fip = statMap.getOrDefault("FIP", 0.0);
        double weight = (fip > 0 && fip < 6.0) ? 1.0 + Math.pow((6.0 - fip) / 3.0, 1.5) / 4.0 : 1.0;

        double g = statMap.getOrDefault("G", 0.0);
        double bb = statMap.getOrDefault("BB", 0.0);
        double whip = statMap.getOrDefault("WHIP", 0.0);
        double so = statMap.getOrDefault("SO", 0.0);
        double era = statMap.getOrDefault("ERA", 0.0);
        double ip = statMap.getOrDefault("IP", 0.0);

        boolean isReliever = g >= 40;
        double gameRatio = isReliever ? Math.min(g / 40.0, 1.5) : Math.min(ip / 144.0, 1.5);

        double bbScore = (1.0 - Math.min(bb / 4.0, 1.0)) * 100;
        double whipScore = (1.0 - Math.min(whip / 2.0, 1.0)) * 100;
        double control = (bbScore * 0.6 + whipScore * 0.4) * weight * gameRatio;

        double soScore = Math.min(so / 150.0, 1.5) * 100;
        double eraScore = (1.0 - Math.min(era / 6.0, 1.0)) * 100;
        double stuff = (soScore * 0.7 + eraScore * 0.3) * weight * gameRatio;

        double stamina = Math.min(ip / 144.0, 1.5) * 100 * weight;

        double overall = isReliever
            ? (control * 0.45 + stuff * 0.45 + stamina * 0.1)
            : (control * 0.35 + stuff * 0.35 + stamina * 0.3);

        return RawPitcherScore.builder()
            .playerId(playerId)
            .control(control)
            .stuff(stuff)
            .stamina(stamina)
            .overall(overall)
            .build();
    }
    
    // 투수 정규화 범위 설정
    private void initPitcherMinMaxMaps(List<RawPitcherScore> rawList, Map<String, Double> minMap, Map<String, Double> maxMap) {
        List<Double> controls = new ArrayList<>();
        List<Double> stuffs = new ArrayList<>();
        List<Double> staminas = new ArrayList<>();
        List<Double> overalls = new ArrayList<>();

        for (RawPitcherScore raw : rawList) {
            controls.add(raw.getControl());
            stuffs.add(raw.getStuff());
            staminas.add(raw.getStamina());
            overalls.add(raw.getOverall());
        }

        minMap.put("control", Collections.min(controls));
        maxMap.put("control", getTopPercentAverage(controls, 0.04));

        minMap.put("stuff", Collections.min(stuffs));
        maxMap.put("stuff", getTopPercentAverage(stuffs, 0.04));

        minMap.put("stamina", Collections.min(staminas));
        maxMap.put("stamina", getTopPercentAverage(staminas, 0.04));

        minMap.put("overall", Collections.min(overalls));
        maxMap.put("overall", getTopPercentAverage(overalls, 0.04));
    }
    
    // 타자 + 투수 오버롤 한 번에 실행 (2020~24)
    public void calculateAllBatterAndPitcher() {

        List<Integer> legacySeasons = List.of(2020, 2021, 2022, 2023, 2024);

        // 타자
        calculateBatterOverallByPosition(legacySeasons, "1B");
        calculateBatterOverallByPosition(legacySeasons, "2B");
        calculateBatterOverallByPosition(legacySeasons, "3B");
        calculateBatterOverallByPosition(legacySeasons, "SS");
        calculateBatterOverallByPosition(legacySeasons, "LF");
        calculateBatterOverallByPosition(legacySeasons, "CF");
        calculateBatterOverallByPosition(legacySeasons, "RF");
        calculateBatterOverallByPosition(legacySeasons, "DH");
        calculateBatterOverallByPosition(legacySeasons, "C");


        // 투수
        calculatePitcherOverall(legacySeasons);
    }
    
    // 타자 + 투수 오버롤 한 번에 실행 (2025)
    public void calculateOnly2025() {

        List<Integer> currentSeason = List.of(2025);

        String[] positions = {"1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH", "C"};
        for (String pos : positions) {
            calculateBatterOverallByPosition(currentSeason, pos);
        }

        calculatePitcherOverall(currentSeason);
    }
    
    // 전체 선수 카드 등급 부여
    public void assignGradesForAll() {

        assignGradesByType("BATTER");
        assignGradesByType("PITCHER");
    }
    
    // (S-D) 등급 분류
    public void assignGradesByType(String type) {

        List<PlayerCardOverall> list = playerCardOverallRepository.findByType(type);
        list.sort(Comparator.comparing(PlayerCardOverall::getOverall).reversed());

        int total = list.size();
        int sCount = (int) Math.ceil(total * 0.02);
        int aCount = sCount + (int) Math.ceil(total * 0.1);
        int bCount = aCount + (int) Math.ceil(total * 0.20);
        int cCount = bCount + (int) Math.ceil(total * 0.40);

        for (int i = 0; i < total; i++) {
            PlayerCardOverall card = list.get(i);
            String grade;

            // 상대평가 + 절대값 하한선 (OVERALL < 85이면 S 불가, < 70이면 A 불가)
            if (i < sCount && card.getOverall() >= 85) grade = "S";
            else if (i < aCount && card.getOverall() >= 70) grade = "A";
            else if (i < bCount) grade = "B";
            else if (i < cCount) grade = "C";
            else grade = "D";

            card.setGrade(grade);
            playerCardOverallRepository.save(card);
        }
    }
       
    @Transactional(readOnly = true)
    public PlayerCardOverallDTO getCardByPlayerAndSeason(int playerId, int season) {

        Optional<PlayerCardOverall> optional = playerCardOverallRepository.findByPlayerIdAndSeason(playerId, season);

        if (optional.isPresent()) {
            PlayerCardOverall card = optional.get();
            PlayerCardOverallDTO dto = new PlayerCardOverallDTO();

            // 기본 능력치 채우기
            dto.setPlayerId(card.getPlayerId());
            dto.setSeason(card.getSeason());
            dto.setType(card.getType());
            dto.setGrade(card.getGrade());
            dto.setPower(card.getPower());
            dto.setContact(card.getContact());
            dto.setDiscipline(card.getDiscipline());
            dto.setSpeed(card.getSpeed());
            dto.setControl(card.getControl());
            dto.setStuff(card.getStuff());
            dto.setStamina(card.getStamina());
            dto.setOverall(card.getOverall());

            // 선수 이름 설정
            playerRepository.findById(playerId).ifPresent(player -> {
                dto.setPlayerName(player.getName());
            });

            if ("PITCHER".equals(card.getType())) {
                // 투수: pitcherStatsRepository에서 스탯 조회
                dto.setPosition("P");
                List<Object[]> stats = pitcherStatsRepository.findStatsRawByPlayerIdAndSeason(playerId, season);
                Map<String, Double> statMap = new HashMap<>();
                for (Object[] row : stats) {
                    String category = (String) row[0];
                    Double value = ((Number) row[1]).doubleValue();
                    statMap.put(category, value);
                }
                dto.setWar(statMap.getOrDefault("WAR", null));
                dto.setEra(statMap.getOrDefault("ERA", null));
                dto.setWhip(statMap.getOrDefault("WHIP", null));
                dto.setWins(statMap.containsKey("W") ? statMap.get("W").intValue() : null);
                dto.setSaves(statMap.containsKey("SV") ? statMap.get("SV").intValue() : null);
                dto.setHolds(statMap.containsKey("HLD") ? statMap.get("HLD").intValue() : null);
            } else {
                // 타자: batterStatsRepository에서 스탯 조회
                List<Object[]> stats = batterStatsRepository.findStatsRawByPlayerIdAndSeason(playerId, season);
                Map<String, Double> statMap = new HashMap<>();
                for (Object[] row : stats) {
                    String category = (String) row[0];
                    Double value = ((Number) row[1]).doubleValue();
                    statMap.put(category, value);
                }

                dto.setAvg(statMap.getOrDefault("AVG", null));
                dto.setOps(statMap.getOrDefault("OPS", null));
                dto.setWar(statMap.getOrDefault("WAR", null));
                dto.setHr(statMap.containsKey("HR") ? statMap.get("HR").intValue() : null);
                dto.setSb(statMap.containsKey("SB") ? statMap.get("SB").intValue() : null);

                if (!stats.isEmpty()) {
                    String position = batterStatsRepository.findPositionByPlayerIdAndSeason(playerId, season);
                    dto.setPosition(position);
                }

                Optional<Object[]> teamInfoOpt = batterStatsRepository.findTeamAndPosition(playerId, season);
                if (teamInfoOpt.isPresent()) {
                    Object[] arr = teamInfoOpt.get();

                    if (arr.length > 0 && arr[0] instanceof Object[]) {
                        Object[] inner = (Object[]) arr[0];
                        dto.setPosition(inner[0] != null ? inner[0].toString() : null);
                        dto.setTeamName(inner[1] != null ? inner[1].toString() : null);
                        dto.setLogoName(inner[2] != null ? inner[2].toString() : null);
                    } else if (arr.length >= 3) {
                        dto.setPosition(arr[0] != null ? arr[0].toString() : null);
                        dto.setTeamName(arr[1] != null ? arr[1].toString() : null);
                        dto.setLogoName(arr[2] != null ? arr[2].toString() : null);
                    }
                }
            }
            
            return dto;
        }

        return null;
    }
   
}
