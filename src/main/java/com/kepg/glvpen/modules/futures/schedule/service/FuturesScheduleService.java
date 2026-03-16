package com.kepg.glvpen.modules.futures.schedule.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.futures.schedule.domain.FuturesSchedule;
import com.kepg.glvpen.modules.futures.schedule.dto.FuturesScheduleDTO;
import com.kepg.glvpen.modules.futures.schedule.repository.FuturesScheduleRepository;
import com.kepg.glvpen.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.glvpen.modules.team.domain.Team;
import com.kepg.glvpen.modules.team.service.TeamService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FuturesScheduleService {

    private final FuturesScheduleRepository repository;
    private final TeamService teamService;

    public void saveOrUpdate(FuturesScheduleDTO dto) {
        Optional<FuturesSchedule> optional = repository.findByMatchDateAndHomeTeamIdAndAwayTeamId(
                dto.getMatchDate(), dto.getHomeTeamId(), dto.getAwayTeamId());

        FuturesSchedule schedule = optional.orElseGet(() -> FuturesSchedule.builder()
                .season(dto.getSeason())
                .matchDate(dto.getMatchDate())
                .homeTeamId(dto.getHomeTeamId())
                .awayTeamId(dto.getAwayTeamId())
                .build());

        schedule.setHomeTeamScore(dto.getHomeTeamScore());
        schedule.setAwayTeamScore(dto.getAwayTeamScore());
        schedule.setStadium(dto.getStadium());
        schedule.setStatus(dto.getStatus());
        schedule.setLeagueType(dto.getLeagueType());
        schedule.setNote(dto.getNote());
        repository.save(schedule);
    }

    public void saveBatch(List<FuturesScheduleDTO> dtos) {
        for (FuturesScheduleDTO dto : dtos) {
            saveOrUpdate(dto);
        }
    }

    /**
     * 퓨처스 리그 월별 일정을 날짜별로 그룹핑하여 반환
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<ScheduleCardView>> getGroupedScheduleByMonth(int year, int month) {
        Timestamp start = Timestamp.valueOf(YearMonth.of(year, month).atDay(1).atStartOfDay());
        Timestamp end = Timestamp.valueOf(YearMonth.of(year, month).atEndOfMonth().atTime(23, 59, 59));
        List<FuturesSchedule> schedules = repository.findByMatchDateBetweenOrderByMatchDateAsc(start, end);

        Map<LocalDate, List<ScheduleCardView>> grouped = new LinkedHashMap<>();

        for (FuturesSchedule fs : schedules) {
            if (fs.getMatchDate() == null) continue;

            LocalDate date = fs.getMatchDate().toLocalDateTime().toLocalDate();

            String homeTeamName = getTeamName(fs.getHomeTeamId());
            String awayTeamName = getTeamName(fs.getAwayTeamId());
            String homeTeamLogo = getTeamLogo(fs.getHomeTeamId());
            String awayTeamLogo = getTeamLogo(fs.getAwayTeamId());

            // note에 경기장 정보, stadium에 취소 사유 저장된 구조
            String stadiumDisplay = fs.getNote() != null ? fs.getNote() : "";
            if (fs.getStadium() != null && !fs.getStadium().isBlank()) {
                stadiumDisplay += " (" + fs.getStadium() + ")";
            }

            String matchTime = fs.getMatchDate().toLocalDateTime().toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

            ScheduleCardView view = ScheduleCardView.builder()
                    .id(fs.getId())
                    .matchDate(fs.getMatchDate())
                    .homeTeamName(homeTeamName)
                    .awayTeamName(awayTeamName)
                    .homeTeamLogo(homeTeamLogo)
                    .awayTeamLogo(awayTeamLogo)
                    .homeTeamScore(fs.getHomeTeamScore())
                    .awayTeamScore(fs.getAwayTeamScore())
                    .stadium(stadiumDisplay)
                    .status(fs.getStatus())
                    .matchTime(matchTime)
                    .build();

            grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(view);
        }

        return grouped;
    }

    private String getTeamName(Integer teamId) {
        if (teamId == null) return "미정";
        Team team = teamService.getTeamById(teamId);
        return team != null ? team.getName() : "팀 " + teamId;
    }

    private String getTeamLogo(Integer teamId) {
        if (teamId == null) return "unknown";
        Team team = teamService.getTeamById(teamId);
        return team != null ? team.getLogoName() : "unknown";
    }
}
