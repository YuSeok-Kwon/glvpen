package com.kepg.glvpen.modules.game.scoreBoard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.game.scoreBoard.domain.ScoreBoard;
import com.kepg.glvpen.modules.game.scoreBoard.repository.ScoreBoardRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreBoardService {

    private final ScoreBoardRepository scoreBoardRepository;

    // 경기 스케줄(scheduleId)에 해당하는 스코어보드를 저장하거나, 기존 값이 있으면 업데이트함
    @Transactional
    public void saveOrUpdate(ScoreBoard scoreBoard) {
        ScoreBoard existing = scoreBoardRepository.findByScheduleId(scoreBoard.getScheduleId());

        if (existing != null) {
            ScoreBoard updated = ScoreBoard.builder()
                .id(existing.getId())
                .scheduleId(scoreBoard.getScheduleId())
                .homeScore(scoreBoard.getHomeScore())
                .awayScore(scoreBoard.getAwayScore())
                .winPitcher(scoreBoard.getWinPitcher())
                .losePitcher(scoreBoard.getLosePitcher())
                .homeInningScores(scoreBoard.getHomeInningScores())
                .awayInningScores(scoreBoard.getAwayInningScores())
                .homeR(scoreBoard.getHomeR())
                .homeH(scoreBoard.getHomeH())
                .homeE(scoreBoard.getHomeE())
                .homeB(scoreBoard.getHomeB())
                .awayR(scoreBoard.getAwayR())
                .awayH(scoreBoard.getAwayH())
                .awayE(scoreBoard.getAwayE())
                .awayB(scoreBoard.getAwayB())
                .crowd(scoreBoard.getCrowd())
                .startTime(scoreBoard.getStartTime())
                .endTime(scoreBoard.getEndTime())
                .gameTime(scoreBoard.getGameTime())
                .build();

            scoreBoardRepository.save(updated);
        } else {
            scoreBoardRepository.save(scoreBoard);
        }
    }
    
    // scheduleId에 해당하는 스코어보드 정보를 조회
    public ScoreBoard findByScheduleId(int scheduleId) {
        return scoreBoardRepository.findByScheduleId(scheduleId);
    }
}
