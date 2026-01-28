package com.kepg.BaseBallLOCK.modules.review.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.game.lineUp.service.LineupService;
import com.kepg.BaseBallLOCK.modules.game.record.service.RecordService;
import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.review.domain.Review;
import com.kepg.BaseBallLOCK.modules.review.dto.CalendarDayDTO;
import com.kepg.BaseBallLOCK.modules.review.dto.GameInfoDTO;
import com.kepg.BaseBallLOCK.modules.review.dto.ReviewDTO;
import com.kepg.BaseBallLOCK.modules.review.repository.ReviewRepository;
import com.kepg.BaseBallLOCK.modules.review.summary.service.ReviewSummaryService;
import com.kepg.BaseBallLOCK.modules.team.service.TeamService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ScheduleService scheduleService;
    private final TeamService teamService;
    private final ReviewRepository reviewRepository;
    private final ReviewSummaryService reviewSummaryService;
    private final LineupService lineupService;
    private final RecordService recordService;

    // 해당 월에 대한 달력 데이터를 생성 (리뷰 여부 및 경기 정보 포함)
    public List<List<CalendarDayDTO>> generateCalendar(int year, int month, int userId, int myTeamId) {

        List<List<CalendarDayDTO>> calendar = new ArrayList<>();

        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();

        LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
        int firstDayOfWeek = firstDayOfMonth.getDayOfWeek().getValue(); // 1=월요일 ~ 7=일요일
        int dayIndex = (firstDayOfWeek + 6) % 7;  // 월요일 시작 보정

        List<CalendarDayDTO> week = new ArrayList<>();

        // 이전 달의 마지막 날들로 앞쪽 빈칸 채우기
        YearMonth prevMonth = yearMonth.minusMonths(1);
        int prevMonthDays = prevMonth.lengthOfMonth();
        
        for (int i = dayIndex - 1; i >= 0; i--) {
            int prevDay = prevMonthDays - i;
            LocalDate prevDate = LocalDate.of(prevMonth.getYear(), prevMonth.getMonthValue(), prevDay);
            
            CalendarDayDTO dayDto = new CalendarDayDTO();
            dayDto.setDayOfMonth(prevDay);
            dayDto.setDate(prevDate);
            dayDto.setScheduleId(null);

            // 헬퍼 메서드로 리뷰 및 경기 정보 설정
            setReviewInfo(dayDto, userId, prevDate);
            setGameInfo(dayDto, prevDate, myTeamId);

            week.add(dayDto);
        }

        // 실제 날짜 채우기
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);

            CalendarDayDTO dayDto = new CalendarDayDTO();
            dayDto.setDayOfMonth(day);
            dayDto.setDate(date);
            dayDto.setScheduleId(null);

            // 헬퍼 메서드로 리뷰 및 경기 정보 설정
            setReviewInfo(dayDto, userId, date);
            setGameInfo(dayDto, date, myTeamId);

            week.add(dayDto);

            // 한 주 단위로 분리
            if (week.size() == 7) {
                calendar.add(week);
                week = new ArrayList<>();
            }
        }

        // 다음 달의 첫 날들로 마지막 주 빈칸 채우기
        if (!week.isEmpty()) {
            YearMonth nextMonth = yearMonth.plusMonths(1);
            int nextDay = 1;
            
            while (week.size() < 7) {
                LocalDate nextDate = LocalDate.of(nextMonth.getYear(), nextMonth.getMonthValue(), nextDay);
                
                CalendarDayDTO dayDto = new CalendarDayDTO();
                dayDto.setDayOfMonth(nextDay);
                dayDto.setDate(nextDate);
                dayDto.setScheduleId(null);

                // 헬퍼 메서드로 리뷰 및 경기 정보 설정
                setReviewInfo(dayDto, userId, nextDate);
                setGameInfo(dayDto, nextDate, myTeamId);

                week.add(dayDto);
                nextDay++;
            }
            calendar.add(week);
        }

        return calendar;
    }
    
    // 팀 ID를 기반으로 팀 색상을 조회
    public String getTeamColorByTeamId(int teamId) {
    	String color = teamService.findColorById(teamId);
        return color;
    }
    
    // 리뷰를 저장하거나 이미 존재하는 경우 수정함 (scheduleId 기준)
    // 저장 이후, 해당 주차의 리뷰 요약을 자동 생성
    @Transactional
    public void saveOrUpdateReview(int userId, ReviewDTO reviewDTO) {

        Review review = new Review();
        review.setUserId(userId);
        review.setScheduleId(reviewDTO.getScheduleId());
        review.setSummary(reviewDTO.getSummary());
        review.setBestPlayer(reviewDTO.getBestPlayer());
        review.setWorstPlayer(reviewDTO.getWorstPlayer());
        review.setFeelings(reviewDTO.getFeelings());
        review.setRating(reviewDTO.getRating());

        List<Review> existingList = reviewRepository.findAllByUserIdAndScheduleId(userId, reviewDTO.getScheduleId());
        if (!existingList.isEmpty()) {
            Review existing = existingList.get(0); // 가장 첫 번째 것만 사용
            review.setId(existing.getId());
            review.setCreatedAt(existing.getCreatedAt());
        } else {
            review.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        }
        review.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

        reviewRepository.save(review);

        Timestamp matchTimestamp = scheduleService.findMatchDateById(review.getScheduleId());
        LocalDate matchDate = matchTimestamp.toLocalDateTime().toLocalDate();
        LocalDate weekStart = matchDate.with(java.time.DayOfWeek.MONDAY);

        reviewSummaryService.generateWeeklyReviewSummary(review.getUserId(), weekStart);
    }
    
    // 리뷰 ID로 단일 리뷰 조회
    public Optional<Review> findById(Integer id) {
        return reviewRepository.findById(id);
    }
    
    // 특정 경기에서 유저 팀(myTeamId)이 포함된 타자/투수 이름을 리스트로 조회 (중복 제거 및 가나다 정렬)
    public List<String> findPlayerNamesByScheduleId(int scheduleId, int myTeamId) {
        Set<String> names = new HashSet<>();

        List<String> batters = lineupService.getBatterNamesByScheduleId(scheduleId, myTeamId);
        List<String> pitchers = recordService.getPitcherNamesByScheduleId(scheduleId, myTeamId);

        names.addAll(batters);
        names.addAll(pitchers);

        List<String> sorted = new ArrayList<>(names);
        sorted.sort(null); // 가나다순 정렬
        return sorted;
    }

    // 헬퍼 메서드: 리뷰 정보 설정
    private void setReviewInfo(CalendarDayDTO dayDto, int userId, LocalDate date) {
        reviewRepository.findByUserIdAndMatchDate(userId, date)
            .ifPresentOrElse(
                review -> {
                    dayDto.setHasReview(true);
                    dayDto.setReviewId(review.getId());
                },
                () -> dayDto.setHasReview(false)
            );
    }

    // 헬퍼 메서드: 경기 정보 설정
    private void setGameInfo(CalendarDayDTO dayDto, LocalDate date, int myTeamId) {
        List<Schedule> schedules = scheduleService.findByMatchDateAndTeam(date, myTeamId);
        List<GameInfoDTO> games = new ArrayList<>();

        if (!schedules.isEmpty()) {
            dayDto.setScheduleId(schedules.get(0).getId());
        }

        for (Schedule schedule : schedules) {
            int homeTeamId = schedule.getHomeTeamId();
            int awayTeamId = schedule.getAwayTeamId();

            if (homeTeamId == myTeamId || awayTeamId == myTeamId) {
                GameInfoDTO game = new GameInfoDTO();
                game.setScheduleId(schedule.getId());
                game.setHomeTeamName(teamService.getTeamNameById(homeTeamId));
                game.setAwayTeamName(teamService.getTeamNameById(awayTeamId));
                game.setHomeScore(schedule.getHomeTeamScore());
                game.setAwayScore(schedule.getAwayTeamScore());
                games.add(game);
            }
        }

        dayDto.setGames(games);
    }
}