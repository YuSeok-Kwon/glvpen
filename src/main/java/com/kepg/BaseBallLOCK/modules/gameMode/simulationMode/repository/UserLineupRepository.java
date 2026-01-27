package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.UserLineup;

public interface UserLineupRepository extends JpaRepository<UserLineup, Integer> {

	// 특정 유저의 모든 라인업 조회
    List<UserLineup> findByUserId(Integer userId);

    // 특정 유저가 해당 타순(orderNum)에 라인업을 등록했는지 여부 확인
    boolean existsByUserIdAndOrderNum(Integer userId, Integer orderNum);

    // 특정 유저의 라인업 전체 삭제
    void deleteByUserId(Integer userId); 

    // 특정 유저의 라인업을 타순 기준으로 정렬해서 조회
    List<UserLineup> findByUserIdOrderByOrderNum(Integer userId);


}
