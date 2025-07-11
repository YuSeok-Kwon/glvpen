package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.repository.PlayerRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto.PlayerCardOverallDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.overall.service.PlayerCardOverallService;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.domain.UserLineup;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.dto.UserLineupDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.repository.UserLineupRepository;

import lombok.RequiredArgsConstructor;

@Transactional
@Service
@RequiredArgsConstructor
public class UserLineupService {

    private final UserLineupRepository userLineupRepository;
    private final PlayerRepository playerRepository;
    private final PlayerCardOverallService playerCardOverallService;

    // 유저 ID로 해당 유저의 라인업 전체를 조회
    public List<UserLineup> getLineupByUserId(Integer userId) {
        return userLineupRepository.findByUserId(userId);
    }
    
    // 유저 ID로 저장된 라인업을 orderNum 기준 정렬 후 카드 능력치 DTO 리스트로 반환
    public List<PlayerCardOverallDTO> getSavedLineup(Integer userId) {

        List<UserLineup> saved = userLineupRepository.findByUserIdOrderByOrderNum(userId);
        List<PlayerCardOverallDTO> result = new ArrayList<>();

        for (UserLineup l : saved) {
            PlayerCardOverallDTO dto = playerCardOverallService.getCardByPlayerAndSeason(l.getPlayerId(), l.getSeason());
            dto.setPosition(l.getPosition());
            result.add(dto);
        }

        return result;
    }
    
    // 유저의 라인업 정보를 저장
    public void save(UserLineupDTO dto, int userId) {

        UserLineup lineup = UserLineup.builder()
            .userId(userId)
            .playerId(dto.getPlayerId())
            .position(dto.getPosition())
            .orderNum(dto.getOrderNum())
            .season(dto.getSeason())
            .build();

        userLineupRepository.save(lineup);
    }

    // 특정 유저의 기존 라인업 데이터를 모두 삭제
    public void clearLineup(Integer userId) {
        userLineupRepository.deleteByUserId(userId);
    }
    
    // 유저 ID로 라인업 정보를 DTO 리스트 형태로 반환
    public List<UserLineupDTO> getUserLineup(Integer userId) {


        List<UserLineup> list = userLineupRepository.findByUserId(userId);

        List<UserLineupDTO> result = new ArrayList<>();
        for (UserLineup u : list) {
            UserLineupDTO dto = new UserLineupDTO();
            dto.setPlayerId(u.getPlayerId());
            dto.setPosition(u.getPosition());
            dto.setOrderNum(u.getOrderNum());
            dto.setSeason(u.getSeason());

            Player player = playerRepository.findById(u.getPlayerId()).orElse(null);
            if (player != null) {
                dto.setPlayerName(player.getName());
            }

            result.add(dto);
        }
        return result;
    }

    
}
