package com.kepg.BaseBallLOCK.modules.user.controller;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.game.service.GameService;
import com.kepg.BaseBallLOCK.modules.player.dto.TopPlayerCardView;
import com.kepg.BaseBallLOCK.modules.player.stats.service.BatterStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.PitcherStatsService;
import com.kepg.BaseBallLOCK.modules.team.domain.Team;
import com.kepg.BaseBallLOCK.modules.team.service.TeamService;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.dto.TeamRankingCardView;
import com.kepg.BaseBallLOCK.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequestMapping("/user")
@Controller
@RequiredArgsConstructor
public class UserController {

    private final TeamService teamService;
    private final ScheduleService scheduleService;
    private final GameService gameService;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;


    @GetMapping("/login-view")
    public String loginView() {
        return "user/login";
    }
    
	// 로그아웃 API
	@GetMapping("/logout")
	public String logout(HttpSession session) {
	    session.invalidate(); 

	    Map<String, String> resultMap = new HashMap<>();
	    resultMap.put("result", "success");
	    return "redirect:/user/login-view";	
	}
	
    @GetMapping("/join-view")
    public String joinView() {
        return "user/join";
    }

    @GetMapping("/find-id-view")
    public String findIdView() {
        return "user/findId";
    }

    @GetMapping("/find-password-view")
    public String findPasswordView() {
        return "user/findPassword";
    }

    @GetMapping("/home")
    public String homeView(Model model, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            return "redirect:/user/login-view";
        }

        int myTeamId = user.getFavoriteTeamId();
        int season = LocalDate.now().getYear();
        
        // 내 팀 정보
        Team myTeam = teamService.getTeamById(myTeamId);
        model.addAttribute("myTeam", myTeam);

        // 오늘 경기
        Schedule schedule = scheduleService.getTodayScheduleByTeam(myTeamId);
        model.addAttribute("schedule", schedule);

        if (schedule != null) {
            int opponentId = schedule.getHomeTeamId() == myTeamId ? schedule.getAwayTeamId() : schedule.getHomeTeamId();

            Team opponentTeam = teamService.getTeamById(opponentId);
            model.addAttribute("opponentTeam", opponentTeam);

            // 최근 전적
            List<String> myRecentResults = scheduleService.getRecentResults(myTeamId);
            List<String> opponentRecentResults = scheduleService.getRecentResults(opponentId);
            model.addAttribute("myRecentResults", myRecentResults);
            Collections.reverse(opponentRecentResults);
            model.addAttribute("opponentRecentResults", opponentRecentResults);

            // 상대 전적
            String myRecord = scheduleService.getHeadToHeadRecord(myTeamId, opponentId);
            String opponentRecord = scheduleService.getHeadToHeadRecord(opponentId, myTeamId);
            model.addAttribute("myRecord", myRecord);
            model.addAttribute("opponentRecord", opponentRecord);
        }

        // 주요 선수
        TopPlayerCardView hitter = batterStatsService.getTopHitter(myTeamId, season);
        TopPlayerCardView pitcher = pitcherStatsService.getTopPitcher(myTeamId, season);
        model.addAttribute("topHitter", hitter);
        model.addAttribute("topPitcher", pitcher);

        List<TeamRankingCardView> rankingList = gameService.getTeamRankingCardViews(season);
        model.addAttribute("rankingList", rankingList);

        return "user/home";
    }
    
}
