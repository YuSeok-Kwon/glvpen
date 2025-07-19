package com.kepg.BaseBallLOCK.modules.game.schedule.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.game.highlight.dto.GameHighlightDTO;
import com.kepg.BaseBallLOCK.modules.game.highlight.service.GameHighlightService;
import com.kepg.BaseBallLOCK.modules.game.lineUp.service.LineupService;
import com.kepg.BaseBallLOCK.modules.game.record.dto.PitcherRecordDTO;
import com.kepg.BaseBallLOCK.modules.game.record.service.RecordService;
import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.dto.GameDetailCardView;
import com.kepg.BaseBallLOCK.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.BaseBallLOCK.modules.game.schedule.repository.ScheduleRepository;
import com.kepg.BaseBallLOCK.modules.game.scoreBoard.domain.ScoreBoard;
import com.kepg.BaseBallLOCK.modules.game.scoreBoard.service.ScoreBoardService;
import com.kepg.BaseBallLOCK.modules.team.domain.Team;
import com.kepg.BaseBallLOCK.modules.team.service.TeamService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final TeamService teamService;
    private final ScoreBoardService scoreBoardService;
    private final LineupService lineupService;
    private final RecordService recordService;
    private final GameHighlightService gameHighlightService;

    // 주어진 경기 일정이 존재하면 업데이트, 없으면 새로 저장
    @Transactional
    public void saveOrUpdate(Schedule newSchedule) {
        Integer existingId = scheduleRepository.findIdByStatizId(newSchedule.getStatizId());

        Schedule schedule;
        if (existingId != null) {
            schedule = scheduleRepository.findById(existingId).orElse(newSchedule);
        } else {
            schedule = newSchedule;
        }

        schedule.setMatchDate(newSchedule.getMatchDate());
        schedule.setStadium(newSchedule.getStadium());
        schedule.setStatus(newSchedule.getStatus());
        schedule.setStatizId(newSchedule.getStatizId());

        if ("종료".equals(newSchedule.getStatus())) {
            schedule.setHomeTeamScore(newSchedule.getHomeTeamScore());
            schedule.setAwayTeamScore(newSchedule.getAwayTeamScore());
        }

        scheduleRepository.save(schedule);
    }

    // 오늘 날짜 기준 가장 이른 경기 한 개 조회
    public Schedule getTodaySchedule() {
        Timestamp start = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        Timestamp end = Timestamp.valueOf(LocalDate.now().atTime(23, 59, 59));
        return scheduleRepository.findFirstByMatchDateBetween(start, end);
    }

    // 오늘 날짜 기준, 특정 팀의 경기 한 개 조회
    public Schedule getTodayScheduleByTeam(int teamId) {
        Timestamp start = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        Timestamp end = Timestamp.valueOf(LocalDate.now().atTime(23, 59, 59));
        return scheduleRepository.findTodayScheduleByTeam(start, end, teamId);
    }

    // 특정 팀의 최근 종료된 5경기 결과(승/패/무)를 리스트로 반환
    public List<String> getRecentResults(int teamId) {
        Timestamp todayStart = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        List<Schedule> matches = scheduleRepository.findRecentSchedules(teamId, todayStart);
        List<String> results = new ArrayList<>();

        for (Schedule match : matches) {
            Integer myScore = null, oppScore = null;
            if (match.getHomeTeamId() == teamId) {
                myScore = match.getHomeTeamScore();
                oppScore = match.getAwayTeamScore();
            } else {
                myScore = match.getAwayTeamScore();
                oppScore = match.getHomeTeamScore();
            }

            if (myScore == null || oppScore == null) {
                results.add("무");
            } else if (myScore > oppScore) {
                results.add("승");
            } else if (myScore < oppScore) {
                results.add("패");
            } else {
                results.add("무");
            }
        }

        Collections.reverse(results);
        return results;
    }

    // 특정 팀 vs 상대 팀의 2025년 전적을 "X승 Y패 Z무" 형식으로 반환
    public String getHeadToHeadRecord(int myTeamId, int opponentTeamId) {
        Timestamp todayStart = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        List<Schedule> matches = scheduleRepository.findHeadToHeadMatches2025(myTeamId, opponentTeamId, todayStart);
        int wins = 0, losses = 0, draws = 0;

        for (Schedule match : matches) {
            Integer myScore, oppScore;
            if (match.getHomeTeamId() == myTeamId) {
                myScore = match.getHomeTeamScore();
                oppScore = match.getAwayTeamScore();
            } else {
                myScore = match.getAwayTeamScore();
                oppScore = match.getHomeTeamScore();
            }

            if (myScore == null || oppScore == null) continue;
            if (myScore > oppScore) wins++;
            else if (myScore < oppScore) losses++;
            else draws++;
        }

        return String.format("%d승 %d패 %d무", wins, losses, draws);
    }

    // 특정 월의 경기 일정을 날짜별로 그룹핑하여 반환 (스케줄 카드용)
    public Map<LocalDate, List<ScheduleCardView>> getGroupedScheduleByMonth(int year, int month) {
        Timestamp start = Timestamp.valueOf(YearMonth.of(year, month).atDay(1).atStartOfDay());
        Timestamp end = Timestamp.valueOf(YearMonth.of(year, month).atEndOfMonth().atTime(23, 59, 59));
        List<Schedule> schedules = scheduleRepository.findByMatchDateBetweenOrderByMatchDate(start, end);

        Map<LocalDate, List<ScheduleCardView>> grouped = new LinkedHashMap<>();

        for (Schedule schedule : schedules) {
            LocalDate date = schedule.getMatchDate().toLocalDateTime().toLocalDate();
            Team homeTeam = teamService.getTeamById(schedule.getHomeTeamId());
            Team awayTeam = teamService.getTeamById(schedule.getAwayTeamId());
            if (homeTeam == null || awayTeam == null) continue;

            ScheduleCardView view = ScheduleCardView.builder()
                    .id(schedule.getId())
                    .matchDate(schedule.getMatchDate())
                    .homeTeamName(homeTeam.getName())
                    .awayTeamName(awayTeam.getName())
                    .homeTeamLogo(homeTeam.getLogoName())
                    .awayTeamLogo(awayTeam.getLogoName())
                    .homeTeamScore(schedule.getHomeTeamScore())
                    .awayTeamScore(schedule.getAwayTeamScore())
                    .stadium(schedule.getStadium())
                    .status(schedule.getStatus())
                    .build();

            grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(view);
        }

        return grouped;
    }
    
    // 오늘 날짜 우선으로 Map정리
    public Map<LocalDate, List<ScheduleCardView>> sortScheduleWithTodayFirst(Map<LocalDate, List<ScheduleCardView>> originalMap, LocalDate today) {
        // 오늘 없으면 그냥 반환 (정렬하지 않음)
        if (!originalMap.containsKey(today)) {
            return originalMap;
        }

        Map<LocalDate, List<ScheduleCardView>> sortedMap = new LinkedHashMap<>();
        sortedMap.put(today, originalMap.get(today));

        List<LocalDate> keys = new ArrayList<>(originalMap.keySet());
        Collections.sort(keys);

        for (LocalDate date : keys) {
            if (!date.equals(today)) {
                sortedMap.put(date, originalMap.get(date));
            }
        }

        return sortedMap;
    }
    
    // 특정 날짜에 해당하는 경기 일정 리스트 반환
    public List<ScheduleCardView> getSchedulesByDate(LocalDate date) {
        Timestamp start = Timestamp.valueOf(date.atStartOfDay());
        Timestamp end = Timestamp.valueOf(date.atTime(23, 59, 59));
        List<Schedule> schedules = scheduleRepository.findByMatchDateBetweenOrderByMatchDate(start, end);

        return schedules.stream().map(schedule -> {
            int homeTeamId = schedule.getHomeTeamId();
            int awayTeamId = schedule.getAwayTeamId();

            return ScheduleCardView.builder()
                    .id(schedule.getId())
                    .matchDate(schedule.getMatchDate())
                    .homeTeamName(teamService.getTeamNameById(homeTeamId))
                    .awayTeamName(teamService.getTeamNameById(awayTeamId))
                    .homeTeamLogo(teamService.getTeamLogoById(homeTeamId))
                    .awayTeamLogo(teamService.getTeamLogoById(awayTeamId))
                    .homeTeamScore(schedule.getHomeTeamScore())
                    .awayTeamScore(schedule.getAwayTeamScore())
                    .stadium(schedule.getStadium())
                    .status(schedule.getStatus())
                    .build();
        }).collect(Collectors.toList());
    }

    // matchId로 경기 상세 정보(GameDetailCardView)를 구성해서 반환
    public ScheduleCardView getScheduleDetailById(int matchId) {
        Optional<Schedule> optional = scheduleRepository.findById(matchId);
        if (optional.isEmpty()) return null;

        Schedule s = optional.get();

        return ScheduleCardView.builder()
                .id(s.getId())
                .matchDate(s.getMatchDate())
                .homeTeamName(teamService.getTeamNameById(s.getHomeTeamId()))
                .awayTeamName(teamService.getTeamNameById(s.getAwayTeamId()))
                .homeTeamLogo(teamService.getTeamLogoById(s.getHomeTeamId()))
                .awayTeamLogo(teamService.getTeamLogoById(s.getAwayTeamId()))
                .homeTeamScore(s.getHomeTeamScore())
                .awayTeamScore(s.getAwayTeamScore())
                .stadium(s.getStadium())
                .status(s.getStatus())
                .build();
    }

    // 경기 ID 찾기 (날짜 + 홈팀 + 원정팀 기준)
    public Integer findScheduleIdByMatchInfo(Timestamp matchDateTime, int homeTeamId, int awayTeamId) {
        return scheduleRepository.findIdByDateAndTeams(matchDateTime, homeTeamId, awayTeamId);
    }

    // 경기 상세 정보 + 타자/투수 기록 + 스코어보드 + 하이라이트 포함한 전체 DTO 반환
    public GameDetailCardView getGameDetail(int matchId) {
        Optional<Schedule> optionalSchedule = scheduleRepository.findById(matchId);
        if (optionalSchedule.isEmpty()) return null;

        Schedule schedule = optionalSchedule.get();
        int homeTeamId = schedule.getHomeTeamId();
        int awayTeamId = schedule.getAwayTeamId();
        String status = schedule.getStatus();

        Team homeTeam = teamService.getTeamById(homeTeamId);
        Team awayTeam = teamService.getTeamById(awayTeamId);

        List<PitcherRecordDTO> allPitcherRecords = recordService.getAllPitcherRecordsByScheduleId(matchId);

        List<String> holdPitchers = new ArrayList<>();
        String savePitcher = null;

        for (PitcherRecordDTO record : allPitcherRecords) {
            if ("HLD".equals(record.getDecision())) {
                holdPitchers.add(record.getPlayerName());
            }
            if (savePitcher == null && "SV".equals(record.getDecision())) {
                savePitcher = record.getPlayerName();
            }
        }
        
        ScoreBoard scoreBoard = scoreBoardService.findByScheduleId(matchId);
        
        List<GameHighlightDTO> highlights = gameHighlightService.findByScheduleId(matchId);

        if (scoreBoard == null) {
            return GameDetailCardView.builder()
                .matchDate(schedule.getMatchDate())
                .stadium(schedule.getStadium())
                .homeTeamName(homeTeam.getName())
                .awayTeamName(awayTeam.getName())
                .homeTeamLogo(homeTeam.getLogoName())
                .awayTeamLogo(awayTeam.getLogoName())
                .homeScore(null)
                .awayScore(null)
                .status(status)
                .homeTeamColor(homeTeam.getColor())
                .awayTeamColor(awayTeam.getColor())
                .build();
        }

        return GameDetailCardView.builder()
                .matchDate(schedule.getMatchDate())
                .status(status)
                .stadium(schedule.getStadium())
                .homeTeamName(homeTeam.getName())
                .awayTeamName(awayTeam.getName())
                .homeTeamLogo(homeTeam.getLogoName())
                .awayTeamLogo(awayTeam.getLogoName())
                .homeScore(schedule.getHomeTeamScore())
                .awayScore(schedule.getAwayTeamScore())
                .homeInningScores(convertInningScores(scoreBoard.getHomeInningScores()))
                .awayInningScores(convertInningScores(scoreBoard.getAwayInningScores()))
                .homeHits(scoreBoard.getHomeH())
                .homeErrors(scoreBoard.getHomeE())
                .awayHits(scoreBoard.getAwayH())
                .awayErrors(scoreBoard.getAwayE())
                .homeBatterLineup(lineupService.getBatterLineup(matchId, homeTeamId))
                .awayBatterLineup(lineupService.getBatterLineup(matchId, awayTeamId))
                .homeBatterRecords(recordService.getBatterRecords(matchId, homeTeamId))
                .awayBatterRecords(recordService.getBatterRecords(matchId, awayTeamId))
                .homePitcherRecords(recordService.getPitcherRecords(matchId, homeTeamId))
                .awayPitcherRecords(recordService.getPitcherRecords(matchId, awayTeamId))
                .winPitcher(scoreBoard.getWinPitcher())
                .losePitcher(scoreBoard.getLosePitcher())
                .savePitcher(savePitcher)
                .holdPitchers(holdPitchers)
                .homeTeamColor(homeTeam.getColor())
                .awayTeamColor(awayTeam.getColor())
                .highlights(highlights)
                .build();
    }

    // 스코어보드 이닝별 점수 문자열("1 0 2") → 정수 리스트로 변환 (내부 메서드용)
    private List<Integer> convertInningScores(String scoreString) {


        List<Integer> scores = new ArrayList<>();

        if (scoreString == null || scoreString.isBlank()) {
            return scores;
        }

        String[] parts = scoreString.split(" ");
        for (int i = 0; i < parts.length; i++) {
            try {
                int score = Integer.parseInt(parts[i]);
                scores.add(score);
            } catch (NumberFormatException e) {
            }
        }

        return scores;
    }
    
    // 이전 경기 ID 조회 (현재 경기 기준)
    public Integer getPrevMatchId(int currentMatchId) {
        Optional<Schedule> current = scheduleRepository.findById(currentMatchId);
        if (current.isEmpty()) return null;

        Timestamp currentDate = current.get().getMatchDate();
        return scheduleRepository.findPrevMatchId(currentDate);
    }

    // 다음 경기 ID 조회 (현재 경기 기준)
    public Integer getNextMatchId(int currentMatchId) {
        Optional<Schedule> current = scheduleRepository.findById(currentMatchId);
        if (current.isEmpty()) return null;

        Timestamp currentDate = current.get().getMatchDate();
        return scheduleRepository.findNextMatchId(currentDate);
    }
    
    // 날짜, 홈팀, 원정팀 기준으로 해당 경기의 scheduleId 조회
    public Integer findIdByDateAndTeams(Timestamp matchDateTime, int homeTeamId, int awayTeamId) {
        return scheduleRepository.findIdByDateAndTeams(matchDateTime, homeTeamId, awayTeamId);
    }
    
    // 특정 날짜에 특정 팀이 출전한 경기 리스트 반환
    public List<Schedule> findByMatchDateAndTeam(LocalDate matchDate, int teamId) {
        Timestamp start = Timestamp.valueOf(matchDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(matchDate.atTime(23, 59, 59));

        return scheduleRepository.findByMatchDateBetweenAndTeam(start, end, teamId);
    }
    
    // 시즌별 각 팀이 치른 경기 수 반환 (Map<teamId, count>)
    public Map<Integer, Integer> getTeamGamesPlayedBySeason(int season) {
        List<Object[]> resultList = scheduleRepository.countGamesByTeam(season);
        Map<Integer, Integer> teamGamesMap = new HashMap<>();

        for (Object[] row : resultList) {
        	Integer teamId = ((Number) row[0]).intValue();
        	Integer gameCount = ((Number) row[1]).intValue();
            teamGamesMap.put(teamId, gameCount);
        }

        return teamGamesMap;
    }
    
    // matchId로 경기 날짜(Timestamp) 반환
    public Timestamp findMatchDateById(int scheduleId) {
    	return scheduleRepository.findMatchDateById(scheduleId);
    }
	
    public Integer findIdByStatizId(int statizId) {
        return scheduleRepository.findIdByStatizId(statizId);
    }
    
    /**
     * 날짜 범위로 경기 일정 조회
     */
    public List<Schedule> getSchedulesByDateRange(Timestamp start, Timestamp end) {
        return scheduleRepository.findByMatchDateBetweenOrderByMatchDate(start, end);
    }

}