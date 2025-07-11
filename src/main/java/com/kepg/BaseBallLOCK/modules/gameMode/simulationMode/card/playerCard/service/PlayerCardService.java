package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.repository.PlayerRepository;
import com.kepg.BaseBallLOCK.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.BaseBallLOCK.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto.PlayerCardDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto.PlayerCardSaveRequest;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.overall.domain.PlayerCardOverall;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.overall.repository.PlayerCardOverallRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.userCard.domain.UserCard;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.userCard.repository.UserCardRepository;
import com.kepg.BaseBallLOCK.modules.team.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlayerCardService {

    private static final List<Integer> AVAILABLE_SEASONS = Arrays.asList(2020, 2021, 2022, 2023, 2024, 2025);

    private final PlayerRepository playerRepository;
    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final TeamRepository teamRepository;
    private final UserCardRepository userCardRepository;
    private final PlayerCardOverallRepository playerCardOverallRepository;

    // 포지션 기군으로 무작위 5장 뽑기
    public List<PlayerCardDTO> drawCardsByPosition(String position) {
        List<PlayerCardDTO> result = new ArrayList<>();
        boolean isBatter = !"P".equals(position);

        List<Integer> seasons = Arrays.asList(2020, 2021, 2022, 2023, 2024, 2025);
        Collections.shuffle(seasons); // 무작위 순서

        List<PlayerCardOverall> allCandidates = new ArrayList<>();
        for (int season : seasons) {
            List<PlayerCardOverall> candidates = isBatter
                    ? playerCardOverallRepository.findByBatterPositionAndSeason(position, season)
                    : playerCardOverallRepository.findPitchersBySeason(season);

            if (candidates != null && !candidates.isEmpty()) {
                allCandidates.addAll(candidates);
            }
        }

        if (allCandidates.isEmpty()) return result;
        Collections.shuffle(allCandidates); // 전체 후보 셔플

        Set<Integer> selectedPlayerIds = new HashSet<>();

        for (PlayerCardOverall overall : allCandidates) {
            if (result.size() >= 5) break;

            Integer playerId = overall.getPlayerId();
            if (selectedPlayerIds.contains(playerId)) continue;

            Player player = playerRepository.findById(playerId).orElse(null);
            if (player == null) continue;

            int season = overall.getSeason();
            Map<String, Double> statMap = getStatMap(playerId, season, isBatter);
            String realPosition = getRealPosition(playerId, season, isBatter);

            PlayerCardDTO cardDTO = buildCardDTO(player, overall, statMap, realPosition, season);
            result.add(cardDTO);
            selectedPlayerIds.add(playerId);
        }

        return result;
    }

    
    // 유저가 저장한 카드 DB에 저장 (중복 방지)
    public void saveCard(PlayerCardSaveRequest dto, Integer userId) {
        boolean exists = userCardRepository.existsByUserIdAndPlayerIdAndSeason(userId, dto.getPlayerId(), dto.getSeason());

        if (!exists) {
            UserCard card = UserCard.builder()
                .userId(userId)
                .playerId(dto.getPlayerId())
                .season(dto.getSeason())
                .grade(dto.getGrade())
                .position(dto.getPosition())
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .build();

            userCardRepository.save(card);
        }
    }

    
    // 포지션별 S등급 카드 전체 조회
    public List<PlayerCardDTO> getAllSGradePlayers(String position) {
        List<PlayerCardDTO> result = new ArrayList<>();

        for (Integer season : AVAILABLE_SEASONS) {
            List<PlayerCardOverall> topCards = playerCardOverallRepository.findTopSGradeByPositionAndSeason(position, season);

            for (PlayerCardOverall overall : topCards) {
                Player player = playerRepository.findById(overall.getPlayerId()).orElse(null);
                if (player == null) {
                    continue;
                }

                Map<String, Double> statMap = getStatMap(player.getId(), season, !"P".equals(position));
                String realPosition = getRealPosition(player.getId(), season, !"P".equals(position));

                PlayerCardDTO dto = buildCardDTO(player, overall, statMap, realPosition, season);
                result.add(dto);
            }
        }

        return result;
    }

   
	// 선수 ID와 시즌 기준으로 타자/투수 raw 스탯 조회 후 Map으로 변환
	private Map<String, Double> getStatMap(Integer playerId, Integer season, boolean isBatter) {
        List<Object[]> rawStats;
        if (isBatter) {
            rawStats = batterStatsRepository.findStatsRawByPlayerIdAndSeason(playerId, season);
        } else {
            rawStats = pitcherStatsRepository.findStatsRawByPlayerIdAndSeason(playerId, season);
        }

        Map<String, Double> statMap = new HashMap<>();
        for (Object[] row : rawStats) {
            if (row.length < 2) continue;
            String category = (String) row[0];
            Double value = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
            statMap.put(category, value);
        }

        return statMap;
    }

   
	// 실제 시즌별 포지션 조회 (타자만 해당)
	private String getRealPosition(Integer playerId, Integer season, boolean isBatter) {
        if (isBatter) {
            return batterStatsRepository.findPositionByPlayerIdAndSeason(playerId, season);
        }
        return "P";
    }

    
	// Player + Overall + Stat 조합으로 카드 DTO 생성
	private PlayerCardDTO buildCardDTO(Player player, PlayerCardOverall overall, Map<String, Double> statMap,
                                       String position, int season) {

        String teamName = teamRepository.findTeamNameById(player.getTeamId());
        String teamColor = teamRepository.findColorById(player.getTeamId());
        String teamLogo = teamRepository.findLogoNameById(player.getTeamId());

        String imagePath = "S".equals(overall.getGrade()) ? "images/player/" + player.getId() + ".png" : null;

        PlayerCardDTO.PlayerCardDTOBuilder builder = PlayerCardDTO.builder()
            .playerId(player.getId())
            .playerName(player.getName())
            .position(position)
            .war(statMap.getOrDefault("WAR", 0.0))
            .grade(overall.getGrade())
            .teamColor(teamColor)
            .imagePath(imagePath)
            .teamName(teamName)
            .teamLogo(teamLogo)
            .season(season)
            .overall(overall.getOverall());

        if (!"P".equals(position)) {
            builder
                .avg(statMap.getOrDefault("AVG", 0.0))
                .hr(statMap.getOrDefault("HR", 0.0).intValue())
                .ops(statMap.getOrDefault("OPS", 0.0))
                .sb(statMap.getOrDefault("SB", 0.0).intValue())
                .power(overall.getPower())
                .contact(overall.getContact())
                .discipline(overall.getDiscipline())
                .speed(overall.getSpeed());
        } else {
            builder
                .era(statMap.getOrDefault("ERA", 0.0))
                .whip(statMap.getOrDefault("WHIP", 0.0))
                .wins(statMap.getOrDefault("W", 0.0).intValue())
                .saves(statMap.getOrDefault("SV", 0.0).intValue())
                .holds(statMap.getOrDefault("HLD", 0.0).intValue())
                .control(overall.getControl())
                .stuff(overall.getStuff())
                .stamina(overall.getStamina());
        }

        return builder.build();
    }
    

}
