package com.kepg.glvpen.modules.player.service;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.dto.PlayerDTO;
import com.kepg.glvpen.modules.player.repository.PlayerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private final PlayerRepository playerRepository;

    // 이름과 팀 ID로 Player 조회
    public Optional<Player> findByNameAndTeamId(String name, int teamId) {
        return playerRepository.findByNameAndTeamId(name, teamId);
    }

    // 이름+팀 ID로 Player가 없으면 새로 생성 후 반환 (동시성 안전)
    @Transactional
    public Player findOrCreatePlayer(PlayerDTO dto) {
        Optional<Player> existing = playerRepository.findByNameAndTeamId(dto.getName(), dto.getTeamId());
        if (existing.isPresent()) {
        	return existing.get();
        }
        try {
            Player newPlayer = Player.builder()
                    .name(dto.getName())
                    .teamId(dto.getTeamId())
                    .build();
            return playerRepository.save(newPlayer);
        } catch (DataIntegrityViolationException e) {
            log.debug("Player 중복 생성 감지, 재조회: name={}, teamId={}", dto.getName(), dto.getTeamId());
            return playerRepository.findByNameAndTeamId(dto.getName(), dto.getTeamId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Player 저장 실패: name=" + dto.getName() + ", teamId=" + dto.getTeamId(), e));
        }
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
