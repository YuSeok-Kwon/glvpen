package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomTeamRepository extends JpaRepository<CustomTeam, Long> {

    /**
     * 사용자의 모든 팀 조회
     */
    List<CustomTeam> findByUserId(Integer userId);

    /**
     * 사용자의 특정 팀 조회
     */
    Optional<CustomTeam> findByIdAndUserId(Long id, Integer userId);

    /**
     * 팀명으로 검색
     */
    List<CustomTeam> findByUserIdAndTeamNameContaining(Integer userId, String teamName);
}
