package com.kepg.BaseBallLOCK.modules.player.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.dto.PlayerDTO;
import com.kepg.BaseBallLOCK.modules.player.repository.PlayerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;

    // 이름과 팀 ID로 Player 조회
    public Optional<Player> findByNameAndTeamId(String name, int teamId) {
        return playerRepository.findByNameAndTeamId(name, teamId);
    }
   
    // 이름+팀 ID로 Player가 없으면 새로 생성 후 반환
    public Player findOrCreatePlayer(PlayerDTO dto) {
        Optional<Player> existing = playerRepository.findByNameAndTeamId(dto.getName(), dto.getTeamId());
        if (existing.isPresent()) {
        	return existing.get();
        }
        Player newPlayer = Player.builder()
                .name(dto.getName())
                .teamId(dto.getTeamId())
                .build();
        return playerRepository.save(newPlayer);
    }
    
    // 해당 팀의 시즌별 WAR 1위 투수 정보 조회
    public List<Object[]> getTopPitcherByTeamAndSeason(int teamId, int season) {
        return playerRepository.findTopPitcherByTeamIdAndSeason(teamId, season);
    }

    // 해당 팀의 시즌별 WAR 1위 타자 정보 조회
    public List<Object[]> getTopHitterByTeamAndSeason(int teamId, int season) {
        return playerRepository.findTopHitterByTeamIdAndSeason(teamId, season);
    }
    
    // 이름과 팀 ID로 새 Player 저장
    public void savePlayer(String name, Integer teamId) {
        Player newPlayer = Player.builder()
                .name(name)
                .teamId(teamId)
                .build();
        playerRepository.save(newPlayer);
    }
}
