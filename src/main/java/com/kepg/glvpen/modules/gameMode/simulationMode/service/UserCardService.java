package com.kepg.glvpen.modules.gameMode.simulationMode.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.repository.PlayerRepository;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.PlayerCardOverallDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.repository.PlayerCardOverallRepository;
import com.kepg.glvpen.modules.gameMode.simulationMode.domain.PlayerCardOverall;
import com.kepg.glvpen.modules.gameMode.simulationMode.domain.UserCard;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.UserCardViewDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.repository.UserCardRepository;
import com.kepg.glvpen.modules.team.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCardService {

    private final UserCardRepository userCardRepository;
    private final PlayerRepository playerRepository;
    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final TeamRepository teamRepository;
    private final PlayerCardOverallRepository playerCardOverallRepository;

    public List<UserCardViewDTO> getUserCardViewList(Integer userId) {

        List<UserCard> userCards = userCardRepository.findByUserId(userId);
        List<UserCardViewDTO> result = new ArrayList<>();

        for (UserCard card : userCards) {
            Player player = playerRepository.findById(card.getPlayerId()).orElse(null);
            if (player == null) continue;

            Optional<PlayerCardOverall> optional = playerCardOverallRepository
                .findByPlayerIdAndSeason(player.getId(), card.getSeason());

            if (optional.isEmpty()) continue;
            PlayerCardOverallDTO cardDTO = convertToDTO(optional.get());

            String teamName = teamRepository.findTeamNameById(player.getTeamId());
            String teamColor = teamRepository.findColorById(player.getTeamId());
            String teamLogo = teamRepository.findLogoNameById(player.getTeamId());

            Map<String, Double> statMap;
            if ("P".equals(card.getPosition())) {
                List<Object[]> raw = pitcherStatsRepository.findStatsRawByPlayerIdAndSeason(player.getId(), card.getSeason());
                statMap = convertToStatMap(raw);
            } else {
                List<Object[]> raw = batterStatsRepository.findStatsRawByPlayerIdAndSeason(player.getId(), card.getSeason());
                statMap = convertToStatMap(raw);
            }

            String imagePath = null;
            if ("S".equals(cardDTO.getGrade())) {
                imagePath = "images/player/" + player.getId() + ".png";
            }

            UserCardViewDTO.UserCardViewDTOBuilder builder = UserCardViewDTO.builder()
                .playerId(player.getId())
                .playerName(player.getName())
                .teamName(teamName)
                .teamColor(teamColor)
                .teamLogo(teamLogo)
                .position(card.getPosition())
                .season(card.getSeason())
                .grade(cardDTO.getGrade())
                .imagePath(imagePath)
                .overall(cardDTO.getOverall())
                .war(statMap.getOrDefault("WAR", 0.0));

            if (!"P".equals(card.getPosition())) {
                builder
                    .avg(statMap.getOrDefault("AVG", 0.0))
                    .hr(statMap.getOrDefault("HR", 0.0).intValue())
                    .ops(statMap.getOrDefault("OPS", 0.0))
                    .sb(statMap.getOrDefault("SB", 0.0).intValue())
                    .power(cardDTO.getPower())
                    .contact(cardDTO.getContact())
                    .discipline(cardDTO.getDiscipline())
                    .speed(cardDTO.getSpeed());
            } else {
                builder
                    .era(statMap.getOrDefault("ERA", 0.0))
                    .whip(statMap.getOrDefault("WHIP", 0.0))
                    .wins(statMap.getOrDefault("W", 0.0).intValue())
                    .saves(statMap.getOrDefault("SV", 0.0).intValue())
                    .holds(statMap.getOrDefault("HLD", 0.0).intValue())
                    .control(cardDTO.getControl())
                    .stuff(cardDTO.getStuff())
                    .stamina(cardDTO.getStamina());
            }

            result.add(builder.build());
        }

        List<UserCardViewDTO> sorted = new ArrayList<>();
        String[] order = {"S", "A", "B", "C", "D"};

        for (String g : order) {
            for (UserCardViewDTO card : result) {
                if (g.equals(card.getGrade())) {
                    sorted.add(card);
                }
            }
        }

        return sorted;
    }

    private Map<String, Double> convertToStatMap(List<Object[]> rawStats) {
        Map<String, Double> statMap = new HashMap<>();
        for (Object[] row : rawStats) {
            if (row.length < 2) continue;
            String category = (String) row[0];
            Double value = (row[1] instanceof Number) ? ((Number) row[1]).doubleValue() : 0.0;
            statMap.put(category, value);
        }
        return statMap;
    }

    public void saveUserCard(Integer userId, Integer playerId, Integer season, String grade, String position) {
        UserCard userCard = UserCard.builder()
            .userId(userId)
            .playerId(playerId)
            .season(season)
            .grade(grade)
            .position(position)
            .createdAt(Timestamp.from(Instant.now()))
            .build();

        userCardRepository.save(userCard);
    }

    public List<UserCard> findByUserId(int userId){
        return userCardRepository.findByUserId(userId);
    }
    
    @Transactional
    public void deleteCard(Integer userId, Integer playerId, Integer season) {
        userCardRepository.deleteByUserIdAndPlayerIdAndSeason(userId, playerId, season);
    }
    
    private PlayerCardOverallDTO convertToDTO(PlayerCardOverall p) {
        return PlayerCardOverallDTO.builder()
            .id(p.getId())
            .playerId(p.getPlayerId())
            .season(p.getSeason())
            .type(p.getType())
            .overall(p.getOverall())
            .grade(p.getGrade())
            .power(p.getPower())
            .contact(p.getContact())
            .discipline(p.getDiscipline())
            .speed(p.getSpeed())
            .control(p.getControl())
            .stuff(p.getStuff())
            .stamina(p.getStamina())
            .build();
    }
    
}