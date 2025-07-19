package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.GameEventQuestion;

public interface GameEventQuestionRepository extends JpaRepository<GameEventQuestion, Integer> {
	
	// 5개의 질문 랜덤으로 추출
    @Query(value = """
        SELECT * FROM gameEventQuestion 
        ORDER BY RAND() 
        LIMIT 5
        """, nativeQuery = true)
    List<GameEventQuestion> getRandomQuestions();
    
    // 5개의 랜덤 타입 추출
    @Query(value = "SELECT * FROM gameEventQuestion WHERE type = :type ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Optional<GameEventQuestion> findRandomByType(@Param("type") String type);
}

