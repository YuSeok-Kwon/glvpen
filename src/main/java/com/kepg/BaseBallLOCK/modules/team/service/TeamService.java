package com.kepg.BaseBallLOCK.modules.team.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.team.domain.Team;
import com.kepg.BaseBallLOCK.modules.team.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;

    // 팀 ID로 Team 엔티티 전체 조회
    public Team getTeamById(int id) {
        Optional<Team> optional = teamRepository.findById(id);
        return optional.orElse(null);
    }
   
    // 팀 ID로 팀 이름 조회
    public String getTeamNameById(int teamId) {
        return teamRepository.findById(teamId)
                             .map(Team::getName)
                             .orElse("팀 없음");
    }
    
    // 팀 ID로 로고 파일명 조회
    public String getTeamLogoById(int teamId) {
        return teamRepository.findById(teamId)
                             .map(Team::getLogoName)
                             .orElse(null);
    }
    // 팀 ID로 컬러 코드 조회
    public String findColorById(int teamId) {
        return teamRepository.findColorById(teamId);
    }
}