package com.kepg.glvpen.modules.game.schedule.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.game.highlight.dto.GameHighlightDTO;
import com.kepg.glvpen.modules.game.highlight.service.GameHighlightService;
import com.kepg.glvpen.modules.game.keyPlayer.domain.GameKeyPlayer;
import com.kepg.glvpen.modules.game.keyPlayer.service.GameKeyPlayerService;
import com.kepg.glvpen.modules.game.lineUp.service.LineupService;
import com.kepg.glvpen.modules.game.record.domain.BatterRecord;
import com.kepg.glvpen.modules.game.record.domain.PitcherRecord;
import com.kepg.glvpen.modules.game.record.dto.PitcherRecordDTO;
import com.kepg.glvpen.modules.game.record.repository.BatterRecordRepository;
import com.kepg.glvpen.modules.game.record.repository.PitcherRecordRepository;
import com.kepg.glvpen.modules.game.record.service.RecordService;
import com.kepg.glvpen.modules.game.schedule.domain.Schedule;
import com.kepg.glvpen.modules.game.schedule.dto.GameDetailCardView;
import com.kepg.glvpen.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.glvpen.modules.game.schedule.repository.ScheduleRepository;
import com.kepg.glvpen.modules.game.scoreBoard.domain.ScoreBoard;
import com.kepg.glvpen.modules.game.scoreBoard.service.ScoreBoardService;
import com.kepg.glvpen.modules.game.summaryRecord.domain.GameSummaryRecord;
import com.kepg.glvpen.modules.game.summaryRecord.service.GameSummaryRecordService;
import com.kepg.glvpen.modules.team.domain.Team;
import com.kepg.glvpen.modules.team.service.TeamService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final TeamService teamService;
    private final ScoreBoardService scoreBoardService;
    private final LineupService lineupService;
    private final RecordService recordService;
    private final BatterRecordRepository batterRecordRepository;
    private final PitcherRecordRepository pitcherRecordRepository;
    private final GameHighlightService gameHighlightService;
    private final GameKeyPlayerService gameKeyPlayerService;
    private final GameSummaryRecordService gameSummaryRecordService;

    // 주어진 경기 일정이 존재하면 업데이트, 없으면 새로 저장
    @Transactional
    public void saveOrUpdate(Schedule newSchedule) {
        // externalId null 체크
        if (newSchedule.getExternalId() == null) {
            log.error("ExternalId가 null인 경기를 저장할 수 없습니다. 날짜: {}, 홈팀ID: {}, 원정팀ID: {}",
                     newSchedule.getMatchDate(), newSchedule.getHomeTeamId(), newSchedule.getAwayTeamId());
            return;
        }

        Integer existingId = scheduleRepository.findIdByExternalId(newSchedule.getExternalId());

        Schedule schedule;
        if (existingId != null) {
            schedule = scheduleRepository.findById(existingId).orElse(newSchedule);
            log.debug("기존 경기 업데이트. externalId: {}, scheduleId: {}", newSchedule.getExternalId(), existingId);
        } else {
            schedule = newSchedule;
            log.debug("새 경기 저장. externalId: {}", newSchedule.getExternalId());

            // 더블헤더 체크 (같은 날짜에 같은 팀의 다른 경기가 있는지)
            if (newSchedule.getMatchDate() != null) {
                java.time.LocalDate date = newSchedule.getMatchDate().toLocalDateTime().toLocalDate();
                java.util.List<Schedule> sameDateGames = findByMatchDateAndTeam(date, newSchedule.getHomeTeamId());
                if (!sameDateGames.isEmpty()) {
                    log.info("더블헤더 감지! 날짜: {}, 팀ID: {}, 기존 경기 수: {}",
                            date, newSchedule.getHomeTeamId(), sameDateGames.size());
                }
            }
        }

        schedule.setMatchDate(newSchedule.getMatchDate());
        schedule.setStadium(newSchedule.getStadium());
        schedule.setStatus(newSchedule.getStatus());
        schedule.setExternalId(newSchedule.getExternalId());
        schedule.setHomeTeamId(newSchedule.getHomeTeamId());
        schedule.setAwayTeamId(newSchedule.getAwayTeamId());

        if ("종료".equals(newSchedule.getStatus())) {
            schedule.setHomeTeamScore(newSchedule.getHomeTeamScore());
            schedule.setAwayTeamScore(newSchedule.getAwayTeamScore());
        }

        scheduleRepository.save(schedule);
    }

    /**
     * KBO gameId 기반으로 경기 일정 저장 또는 업데이트
     * KBO 크롤러에서 사용
     */
    @Transactional
    public void saveOrUpdateByKboGameId(Schedule newSchedule) {
        if (newSchedule.getKboGameId() == null || newSchedule.getKboGameId().isBlank()) {
            log.error("kboGameId가 null/빈값인 경기를 저장할 수 없습니다. 날짜: {}, 홈팀ID: {}, 원정팀ID: {}",
                     newSchedule.getMatchDate(), newSchedule.getHomeTeamId(), newSchedule.getAwayTeamId());
            return;
        }

        Integer existingId = scheduleRepository.findIdByKboGameId(newSchedule.getKboGameId());

        Schedule schedule;
        if (existingId != null) {
            schedule = scheduleRepository.findById(existingId).orElse(newSchedule);
            log.debug("기존 경기 업데이트. kboGameId: {}, scheduleId: {}", newSchedule.getKboGameId(), existingId);
        } else {
            schedule = newSchedule;
            log.debug("새 경기 저장. kboGameId: {}", newSchedule.getKboGameId());
        }

        schedule.setMatchDate(newSchedule.getMatchDate());
        schedule.setStadium(newSchedule.getStadium());
        schedule.setStatus(newSchedule.getStatus());
        schedule.setKboGameId(newSchedule.getKboGameId());
        schedule.setHomeTeamId(newSchedule.getHomeTeamId());
        schedule.setAwayTeamId(newSchedule.getAwayTeamId());
        if (newSchedule.getSeriesType() != null) {
            schedule.setSeriesType(newSchedule.getSeriesType());
        }
        if (newSchedule.getKboSeriesCode() != null) {
            schedule.setKboSeriesCode(newSchedule.getKboSeriesCode());
        }

        if ("종료".equals(newSchedule.getStatus())) {
            schedule.setHomeTeamScore(newSchedule.getHomeTeamScore());
            schedule.setAwayTeamScore(newSchedule.getAwayTeamScore());
        }

        scheduleRepository.save(schedule);
    }

    public Integer findIdByKboGameId(String kboGameId) {
        return scheduleRepository.findIdByKboGameId(kboGameId);
    }

    public Optional<Schedule> findByKboGameId(String kboGameId) {
        return scheduleRepository.findByKboGameId(kboGameId);
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

    // 특정 팀 vs 상대 팀의 현재 시즌 전적을 "X승 Y패 Z무" 형식으로 반환
    public String getHeadToHeadRecord(int myTeamId, int opponentTeamId) {
        LocalDate today = LocalDate.now();
        Timestamp todayStart = Timestamp.valueOf(today.atStartOfDay());
        Timestamp seasonStart = Timestamp.valueOf(LocalDate.of(today.getYear(), 1, 1).atStartOfDay());
        List<Schedule> matches = scheduleRepository.findHeadToHeadMatchesBySeason(myTeamId, opponentTeamId, todayStart, seasonStart);
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

    // 특정 시즌 + 시리즈타입의 첫 경기 월 조회
    public Integer getFirstMonthBySeriesType(int year, String seriesType) {
        return scheduleRepository.findFirstMonthBySeriesType(year, seriesType);
    }

    // 특정 월의 경기 일정을 날짜별로 그룹핑하여 반환 (스케줄 카드용)
    public Map<LocalDate, List<ScheduleCardView>> getGroupedScheduleByMonth(int year, int month) {
        return getGroupedScheduleByMonth(year, month, null);
    }

    // 특정 월/시리즈타입의 경기 일정을 날짜별로 그룹핑하여 반환
    public Map<LocalDate, List<ScheduleCardView>> getGroupedScheduleByMonth(int year, int month, String seriesType) {
        Timestamp start = Timestamp.valueOf(YearMonth.of(year, month).atDay(1).atStartOfDay());
        Timestamp end = Timestamp.valueOf(YearMonth.of(year, month).atEndOfMonth().atTime(23, 59, 59));
        List<Schedule> schedules;
        if (seriesType != null) {
            schedules = scheduleRepository.findByMatchDateBetweenAndSeriesType(start, end, seriesType);
        } else {
            schedules = scheduleRepository.findByMatchDateBetweenOrderByMatchDate(start, end);
        }

        Map<LocalDate, List<ScheduleCardView>> grouped = new LinkedHashMap<>();

        for (Schedule schedule : schedules) {
            if (schedule.getMatchDate() == null) {
                log.warn("경기 날짜가 null입니다. scheduleId: {}", schedule.getId());
                continue;
            }

            LocalDate date = schedule.getMatchDate().toLocalDateTime().toLocalDate();
            Team homeTeam = teamService.getTeamById(schedule.getHomeTeamId());
            Team awayTeam = teamService.getTeamById(schedule.getAwayTeamId());

            // 팀 정보가 없는 경우 로그 출력 및 기본값 사용
            if (homeTeam == null) {
                log.warn("홈팀을 찾을 수 없습니다. scheduleId: {}, homeTeamId: {}, 날짜: {}",
                         schedule.getId(), schedule.getHomeTeamId(), date);
            }
            if (awayTeam == null) {
                log.warn("원정팀을 찾을 수 없습니다. scheduleId: {}, awayTeamId: {}, 날짜: {}",
                         schedule.getId(), schedule.getAwayTeamId(), date);
            }

            // externalId 누락 경고
            if (schedule.getExternalId() == null) {
                log.warn("ExternalId가 null입니다. scheduleId: {}, 날짜: {}, 홈팀: {}, 원정팀: {}",
                         schedule.getId(), date,
                         homeTeam != null ? homeTeam.getName() : "알 수 없음",
                         awayTeam != null ? awayTeam.getName() : "알 수 없음");
            }

            // 경기 시간 추출 (HH:mm)
            String matchTime = schedule.getMatchDate().toLocalDateTime().toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

            ScheduleCardView view = ScheduleCardView.builder()
                    .id(schedule.getId())
                    .externalId(schedule.getExternalId())
                    .matchDate(schedule.getMatchDate())
                    .homeTeamName(homeTeam != null ? homeTeam.getName() : "알 수 없는 팀 (ID: " + schedule.getHomeTeamId() + ")")
                    .awayTeamName(awayTeam != null ? awayTeam.getName() : "알 수 없는 팀 (ID: " + schedule.getAwayTeamId() + ")")
                    .homeTeamLogo(homeTeam != null ? homeTeam.getLogoName() : "unknown")
                    .awayTeamLogo(awayTeam != null ? awayTeam.getLogoName() : "unknown")
                    .homeTeamScore(schedule.getHomeTeamScore())
                    .awayTeamScore(schedule.getAwayTeamScore())
                    .stadium(schedule.getStadium())
                    .status(schedule.getStatus())
                    .matchTime(matchTime)
                    .build();

            grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(view);
        }

        // 더블헤더 정보 추가 (그룹핑 후 처리)
        for (Map.Entry<LocalDate, List<ScheduleCardView>> entry : grouped.entrySet()) {
            enrichDoubleHeaderInfo(entry.getValue());
        }

        return grouped;
    }

    /**
     * 같은 날짜의 경기 리스트에 더블헤더 정보 추가
     */
    private void enrichDoubleHeaderInfo(List<ScheduleCardView> games) {
        // 팀별 경기 수 카운트
        Map<String, List<ScheduleCardView>> teamGames = new HashMap<>();

        for (ScheduleCardView game : games) {
            String homeTeam = game.getHomeTeamName();
            String awayTeam = game.getAwayTeamName();

            teamGames.computeIfAbsent(homeTeam, k -> new ArrayList<>()).add(game);
            teamGames.computeIfAbsent(awayTeam, k -> new ArrayList<>()).add(game);
        }

        // 더블헤더 표시
        for (Map.Entry<String, List<ScheduleCardView>> entry : teamGames.entrySet()) {
            List<ScheduleCardView> teamGameList = entry.getValue();
            if (teamGameList.size() >= 2) {
                // 시간순 정렬
                teamGameList.sort((a, b) -> a.getMatchDate().compareTo(b.getMatchDate()));

                // 더블헤더 정보 설정
                for (int i = 0; i < teamGameList.size(); i++) {
                    teamGameList.get(i).setIsDoubleHeader(true);
                    teamGameList.get(i).setDoubleHeaderSeq(i + 1);
                }
            }
        }
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

            String homeTeamName = teamService.getTeamNameById(homeTeamId);
            String awayTeamName = teamService.getTeamNameById(awayTeamId);
            String homeTeamLogo = teamService.getTeamLogoById(homeTeamId);
            String awayTeamLogo = teamService.getTeamLogoById(awayTeamId);

            // 팀 정보가 없는 경우 로그 출력
            if ("팀 없음".equals(homeTeamName)) {
                log.warn("홈팀 정보 없음. scheduleId: {}, homeTeamId: {}", schedule.getId(), homeTeamId);
            }
            if ("팀 없음".equals(awayTeamName)) {
                log.warn("원정팀 정보 없음. scheduleId: {}, awayTeamId: {}", schedule.getId(), awayTeamId);
            }

            return ScheduleCardView.builder()
                    .id(schedule.getId())
                    .externalId(schedule.getExternalId())
                    .matchDate(schedule.getMatchDate())
                    .homeTeamName(homeTeamName)
                    .awayTeamName(awayTeamName)
                    .homeTeamLogo(homeTeamLogo != null ? homeTeamLogo : "unknown")
                    .awayTeamLogo(awayTeamLogo != null ? awayTeamLogo : "unknown")
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
                .externalId(s.getExternalId())
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

        List<GameKeyPlayer> mvpPlayers = gameKeyPlayerService.findByMetric(matchId, "GAME_WPA_RT");
        List<GameKeyPlayer> keyMoments = gameKeyPlayerService.findByMetric(matchId, "WPA_RT");

        // MVP 기여도 퍼센트 볼드 처리 (예: "19.8%" → "<b>19.8%</b>")
        for (GameKeyPlayer m : mvpPlayers) {
            if (m.getRecordInfo() != null) {
                m.setRecordInfo(m.getRecordInfo().replaceAll(
                        "(\\d+\\.?\\d*%)", "<b>$1</b>"));
            }
        }

        // 결정적 장면의 승리확률 부분을 볼드체로 강조
        for (GameKeyPlayer m : keyMoments) {
            if (m.getRecordInfo() != null) {
                m.setRecordInfo(m.getRecordInfo().replaceAll(
                        "(승리확률\\s*[\\d.]+%\\s*→\\s*[\\d.]+%)",
                        "<b>$1</b>"));
            }
        }

        // 전체 키 플레이어 metric별 그룹핑
        Map<String, List<GameKeyPlayer>> keyPlayersByMetric = gameKeyPlayerService.findAllGroupedByMetric(matchId);

        // 박스스코어 기반 주요 기록 보강 (KBO API에 키플레이어 데이터가 부족한 경우)
        enrichHitterKeyPlayers(matchId, keyPlayersByMetric);
        enrichPitcherKeyPlayers(matchId, keyPlayersByMetric);

        // 상세기록 (결승타, 홈런, 2루타, 도루, 심판 등)
        List<GameSummaryRecord> summaryRecords = gameSummaryRecordService.findByScheduleId(matchId);

        return GameDetailCardView.builder()
                .id(schedule.getId())
                .externalId(schedule.getExternalId())
                .matchDate(schedule.getMatchDate())
                .status(status)
                .stadium(schedule.getStadium())
                .homeTeamName(homeTeam.getName())
                .homeTeamId(homeTeamId)
                .awayTeamName(awayTeam.getName())
                .awayTeamId(awayTeamId)
                .homeTeamLogo(homeTeam.getLogoName())
                .awayTeamLogo(awayTeam.getLogoName())
                .homeTeamScore(schedule.getHomeTeamScore())
                .awayTeamScore(schedule.getAwayTeamScore())
                .homeInningScores(scoreBoard != null ? convertInningScores(scoreBoard.getHomeInningScores()) : new ArrayList<>())
                .awayInningScores(scoreBoard != null ? convertInningScores(scoreBoard.getAwayInningScores()) : new ArrayList<>())
                .homeR(scoreBoard != null ? scoreBoard.getHomeR() : 0)
                .homeH(scoreBoard != null ? scoreBoard.getHomeH() : 0)
                .homeE(scoreBoard != null ? scoreBoard.getHomeE() : 0)
                .homeB(scoreBoard != null ? scoreBoard.getHomeB() : 0)
                .awayR(scoreBoard != null ? scoreBoard.getAwayR() : 0)
                .awayH(scoreBoard != null ? scoreBoard.getAwayH() : 0)
                .awayE(scoreBoard != null ? scoreBoard.getAwayE() : 0)
                .awayB(scoreBoard != null ? scoreBoard.getAwayB() : 0)
                .homeBatterLineup(lineupService.getBatterLineup(matchId, homeTeamId))
                .awayBatterLineup(lineupService.getBatterLineup(matchId, awayTeamId))
                .homeBatterRecords(recordService.getBatterRecords(matchId, homeTeamId))
                .awayBatterRecords(recordService.getBatterRecords(matchId, awayTeamId))
                .homePitcherRecords(recordService.getPitcherRecords(matchId, homeTeamId))
                .awayPitcherRecords(recordService.getPitcherRecords(matchId, awayTeamId))
                .winPitcher(scoreBoard != null ? scoreBoard.getWinPitcher() : null)
                .losePitcher(scoreBoard != null ? scoreBoard.getLosePitcher() : null)
                .savePitcher(savePitcher)
                .holdPitchers(holdPitchers)
                .homeTeamColor(homeTeam.getColor())
                .awayTeamColor(awayTeam.getColor())
                .highlights(highlights)
                .mvpPlayers(mvpPlayers)
                .keyMoments(keyMoments)
                .keyPlayersByMetric(keyPlayersByMetric)
                .summaryRecords(summaryRecords)
                .crowd(scoreBoard != null ? scoreBoard.getCrowd() : null)
                .startTime(scoreBoard != null ? scoreBoard.getStartTime() : null)
                .endTime(scoreBoard != null ? scoreBoard.getEndTime() : null)
                .gameTime(scoreBoard != null ? scoreBoard.getGameTime() : null)
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
                log.warn("이닝별 점수 파싱 실패 - 값: '{}', 전체 문자열: '{}'", parts[i], scoreString);
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
	
    public Integer findIdByExternalId(int externalId) {
        return scheduleRepository.findIdByExternalId(externalId);
    }
    
    public Optional<Schedule> findByExternalId(int externalId) {
        return scheduleRepository.findByExternalId(externalId);
    }
    
    /**
     * 날짜 범위로 경기 일정 조회
     */
    public List<Schedule> getSchedulesByDateRange(Timestamp start, Timestamp end) {
        return scheduleRepository.findByMatchDateBetweenOrderByMatchDate(start, end);
    }
    
    // 특정 기간 및 팀 기준으로 경기 목록 조회 (N+1 쿼리 방지용)
    public List<Schedule> findByMatchDateBetweenAndTeam(Timestamp start, Timestamp end, int teamId) {
        return scheduleRepository.findByMatchDateBetweenAndTeam(start, end, teamId);
    }

    // ======== 더블헤더 관련 유틸리티 메서드 ========

    /**
     * 더블헤더 여부 확인
     * 같은 날짜에 같은 팀이 출전하는 경기가 2개 이상인지 확인
     */
    public boolean isDoubleHeader(LocalDate date, int teamId) {
        List<Schedule> games = findByMatchDateAndTeam(date, teamId);
        return games.size() >= 2;
    }

    /**
     * 더블헤더 경기 목록 조회 (시간 순 정렬)
     */
    public List<Schedule> getDoubleHeaderGames(LocalDate date, int teamId) {
        List<Schedule> games = findByMatchDateAndTeam(date, teamId);
        games.sort((a, b) -> a.getMatchDate().compareTo(b.getMatchDate()));
        return games;
    }

    /**
     * 더블헤더에서 몇 번째 경기인지 반환 (1-based index)
     * 해당 날짜에 경기가 1개면 0 반환, 2개 이상이면 순서 반환
     */
    public int getDoubleHeaderSequence(int scheduleId) {
        Optional<Schedule> optSchedule = scheduleRepository.findById(scheduleId);
        if (optSchedule.isEmpty()) return 0;

        Schedule schedule = optSchedule.get();
        LocalDate date = schedule.getMatchDate().toLocalDateTime().toLocalDate();
        int homeTeamId = schedule.getHomeTeamId();

        List<Schedule> games = getDoubleHeaderGames(date, homeTeamId);
        if (games.size() <= 1) return 0; // 더블헤더 아님

        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).getId().equals(scheduleId)) {
                return i + 1; // 1-based index
            }
        }
        return 0;
    }

    /**
     * 더블헤더 검증: 같은 날짜/같은 팀의 경기가 중복 externalId를 가지고 있는지 확인
     */
    public void validateDoubleHeaderIntegrity(LocalDate date, int teamId) {
        List<Schedule> games = findByMatchDateAndTeam(date, teamId);
        if (games.size() < 2) return;

        Map<Integer, Integer> externalIdCount = new HashMap<>();
        for (Schedule game : games) {
            Integer externalId = game.getExternalId();
            if (externalId != null) {
                externalIdCount.put(externalId, externalIdCount.getOrDefault(externalId, 0) + 1);
            }
        }

        for (Map.Entry<Integer, Integer> entry : externalIdCount.entrySet()) {
            if (entry.getValue() > 1) {
                log.error("중복된 ExternalId 발견! externalId: {}, 중복 개수: {}, 날짜: {}, 팀ID: {}",
                         entry.getKey(), entry.getValue(), date, teamId);
            }
        }
    }

    // 박스스코어 기반 타자 주요 기록을 keyPlayersByMetric에 보강
    private void enrichHitterKeyPlayers(int matchId, Map<String, List<GameKeyPlayer>> keyPlayersByMetric) {
        List<BatterRecord> batterRecords = batterRecordRepository.findByScheduleId(matchId);
        if (batterRecords == null || batterRecords.isEmpty()) return;

        // 홈런 (1개 이상)
        if (!keyPlayersByMetric.containsKey("HR_CN")) {
            List<GameKeyPlayer> hrPlayers = extractTopBatters(batterRecords, matchId, "HR_CN",
                    BatterRecord::getHr, 1, "%d홈런");
            if (!hrPlayers.isEmpty()) keyPlayersByMetric.put("HR_CN", hrPlayers);
        }

        // 안타 (2개 이상)
        if (!keyPlayersByMetric.containsKey("HIT_CN")) {
            List<GameKeyPlayer> hitPlayers = extractTopBatters(batterRecords, matchId, "HIT_CN",
                    BatterRecord::getHits, 2, "%d안타");
            if (!hitPlayers.isEmpty()) keyPlayersByMetric.put("HIT_CN", hitPlayers);
        }

        // 타점 (2개 이상)
        if (!keyPlayersByMetric.containsKey("RBI_CN")) {
            List<GameKeyPlayer> rbiPlayers = extractTopBatters(batterRecords, matchId, "RBI_CN",
                    BatterRecord::getRbi, 2, "%d타점");
            if (!rbiPlayers.isEmpty()) keyPlayersByMetric.put("RBI_CN", rbiPlayers);
        }

        // 도루 (1개 이상)
        if (!keyPlayersByMetric.containsKey("SB_CN")) {
            List<GameKeyPlayer> sbPlayers = extractTopBatters(batterRecords, matchId, "SB_CN",
                    BatterRecord::getSb, 1, "%d도루");
            if (!sbPlayers.isEmpty()) keyPlayersByMetric.put("SB_CN", sbPlayers);
        }

        // 득점 (2개 이상)
        if (!keyPlayersByMetric.containsKey("RUNS_CN")) {
            List<GameKeyPlayer> runsPlayers = extractTopBatters(batterRecords, matchId, "RUNS_CN",
                    BatterRecord::getRuns, 2, "%d득점");
            if (!runsPlayers.isEmpty()) keyPlayersByMetric.put("RUNS_CN", runsPlayers);
        }
    }

    // 박스스코어 기반 투수 주요 기록을 keyPlayersByMetric에 보강
    private void enrichPitcherKeyPlayers(int matchId, Map<String, List<GameKeyPlayer>> keyPlayersByMetric) {
        List<PitcherRecord> pitcherRecords = pitcherRecordRepository.findByScheduleId(matchId);
        if (pitcherRecords == null || pitcherRecords.isEmpty()) return;

        // 탈삼진 (1개 이상, 높을수록 좋음)
        if (!keyPlayersByMetric.containsKey("KK_CN")) {
            List<GameKeyPlayer> kkPlayers = extractTopPitchers(pitcherRecords, matchId, "KK_CN",
                    p -> p.getStrikeouts() != null ? p.getStrikeouts() : 0,
                    1, true, "%d삼진");
            if (!kkPlayers.isEmpty()) keyPlayersByMetric.put("KK_CN", kkPlayers);
        }

        // 방어율 (1이닝 이상 등판 투수만, 낮을수록 좋음)
        if (!keyPlayersByMetric.containsKey("ERA_RT")) {
            List<PitcherRecord> qualified = pitcherRecords.stream()
                    .filter(p -> p.getPlayer() != null && p.getInnings() != null && p.getInnings() >= 1.0)
                    .collect(Collectors.toList());
            List<GameKeyPlayer> eraPlayers = extractTopPitchersByDouble(qualified, matchId, "ERA_RT",
                    p -> p.getInnings() > 0 ? (p.getEarnedRuns() != null ? p.getEarnedRuns() : 0) * 9.0 / p.getInnings() : 99.0,
                    false, "ERA %.2f");
            if (!eraPlayers.isEmpty()) keyPlayersByMetric.put("ERA_RT", eraPlayers);
        }

        // WHIP (1이닝 이상 등판 투수만, 낮을수록 좋음)
        if (!keyPlayersByMetric.containsKey("WHIP_RT")) {
            List<PitcherRecord> qualified = pitcherRecords.stream()
                    .filter(p -> p.getPlayer() != null && p.getInnings() != null && p.getInnings() >= 1.0)
                    .collect(Collectors.toList());
            List<GameKeyPlayer> whipPlayers = extractTopPitchersByDouble(qualified, matchId, "WHIP_RT",
                    p -> p.getInnings() > 0 ? ((p.getHits() != null ? p.getHits() : 0) + (p.getBb() != null ? p.getBb() : 0)) / p.getInnings() : 99.0,
                    false, "WHIP %.2f");
            if (!whipPlayers.isEmpty()) keyPlayersByMetric.put("WHIP_RT", whipPlayers);
        }

        // 이닝 (3이닝 이상, 높을수록 좋음)
        if (!keyPlayersByMetric.containsKey("INN2_CN")) {
            List<GameKeyPlayer> innPlayers = extractTopPitchersByDouble(
                    pitcherRecords.stream()
                            .filter(p -> p.getPlayer() != null && p.getInnings() != null && p.getInnings() >= 3.0)
                            .collect(Collectors.toList()),
                    matchId, "INN2_CN",
                    p -> p.getInnings() != null ? p.getInnings() : 0.0,
                    true, "%s이닝");
            if (!innPlayers.isEmpty()) keyPlayersByMetric.put("INN2_CN", innPlayers);
        }

        // 구원 삼진 (비선발 투수 중 삼진 1개 이상)
        if (!keyPlayersByMetric.containsKey("RELIEF_KK_CN")) {
            List<PitcherRecord> relievers = pitcherRecords.stream()
                    .filter(p -> p.getPlayer() != null && !"선발".equals(p.getEntryType()))
                    .collect(Collectors.toList());
            List<GameKeyPlayer> reliefKkPlayers = extractTopPitchers(relievers, matchId, "RELIEF_KK_CN",
                    p -> p.getStrikeouts() != null ? p.getStrikeouts() : 0,
                    1, true, "%d삼진");
            if (!reliefKkPlayers.isEmpty()) keyPlayersByMetric.put("RELIEF_KK_CN", reliefKkPlayers);
        }
    }

    // 정수 지표 기준 상위 투수 추출 (공동 순위 포함, 최대 상위 3순위)
    private List<GameKeyPlayer> extractTopPitchers(List<PitcherRecord> records, int matchId,
            String metric, java.util.function.ToIntFunction<PitcherRecord> statExtractor,
            int minValue, boolean descending, String infoFormat) {
        List<PitcherRecord> filtered = records.stream()
                .filter(r -> r.getPlayer() != null && statExtractor.applyAsInt(r) >= minValue)
                .sorted(descending
                        ? Comparator.comparingInt(statExtractor).reversed()
                        : Comparator.comparingInt(statExtractor))
                .collect(Collectors.toList());

        List<GameKeyPlayer> result = new ArrayList<>();
        int rank = 1;
        int prevValue = descending ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (PitcherRecord r : filtered) {
            int value = statExtractor.applyAsInt(r);
            if (value != prevValue) {
                rank = result.size() + 1;
                if (rank > 3) break;
                prevValue = value;
            }
            result.add(GameKeyPlayer.builder()
                    .scheduleId(matchId)
                    .playerType("PITCHER")
                    .metric(metric)
                    .ranking(rank)
                    .playerName(r.getPlayer().getName())
                    .teamId(r.getTeamId())
                    .recordInfo(String.format(infoFormat, value))
                    .build());
        }
        return result;
    }

    // 실수 지표 기준 상위 투수 추출 (ERA, WHIP, 이닝)
    private List<GameKeyPlayer> extractTopPitchersByDouble(List<PitcherRecord> records, int matchId,
            String metric, java.util.function.ToDoubleFunction<PitcherRecord> statExtractor,
            boolean descending, String infoFormat) {
        List<PitcherRecord> sorted = records.stream()
                .filter(r -> r.getPlayer() != null)
                .sorted(descending
                        ? Comparator.comparingDouble(statExtractor).reversed()
                        : Comparator.comparingDouble(statExtractor))
                .collect(Collectors.toList());

        List<GameKeyPlayer> result = new ArrayList<>();
        int rank = 1;
        double prevValue = descending ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (PitcherRecord r : sorted) {
            double value = statExtractor.applyAsDouble(r);
            if (Math.abs(value - prevValue) > 0.001) {
                rank = result.size() + 1;
                if (rank > 3) break;
                prevValue = value;
            }
            // 이닝은 야구 표기법으로 포맷
            String info;
            if (infoFormat.contains("%s")) {
                info = String.format(infoFormat, formatInnings(value));
            } else {
                info = String.format(infoFormat, value);
            }
            result.add(GameKeyPlayer.builder()
                    .scheduleId(matchId)
                    .playerType("PITCHER")
                    .metric(metric)
                    .ranking(rank)
                    .playerName(r.getPlayer().getName())
                    .teamId(r.getTeamId())
                    .recordInfo(info)
                    .build());
        }
        return result;
    }

    // 이닝 야구 표기법 (5.333→"5 1/3")
    private String formatInnings(double innings) {
        int whole = (int) innings;
        double frac = innings - whole;
        if (frac < 0.1) return String.valueOf(whole);
        else if (frac < 0.4) return whole == 0 ? "1/3" : whole + " 1/3";
        else if (frac < 0.7) return whole == 0 ? "2/3" : whole + " 2/3";
        else return String.valueOf(whole + 1);
    }

    // 특정 지표 기준으로 상위 타자 추출 (공동 순위 포함, 최대 상위 3순위)
    private List<GameKeyPlayer> extractTopBatters(List<BatterRecord> records, int matchId,
            String metric, java.util.function.ToIntFunction<BatterRecord> statExtractor,
            int minValue, String infoFormat) {
        List<BatterRecord> filtered = records.stream()
                .filter(r -> r.getPlayer() != null && statExtractor.applyAsInt(r) >= minValue)
                .sorted(Comparator.comparingInt(statExtractor).reversed())
                .collect(Collectors.toList());

        List<GameKeyPlayer> result = new ArrayList<>();
        int rank = 1;
        int prevValue = Integer.MAX_VALUE;
        for (BatterRecord r : filtered) {
            int value = statExtractor.applyAsInt(r);
            if (value != prevValue) {
                rank = result.size() + 1;
                if (rank > 3) break;
                prevValue = value;
            }
            result.add(GameKeyPlayer.builder()
                    .scheduleId(matchId)
                    .playerType("HITTER")
                    .metric(metric)
                    .ranking(rank)
                    .playerName(r.getPlayer().getName())
                    .teamId(r.getTeamId())
                    .recordInfo(String.format(infoFormat, value))
                    .build());
        }
        return result;
    }

}