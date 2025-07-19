package com.kepg.BaseBallLOCK.modules.user.controller;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
import com.kepg.BaseBallLOCK.modules.user.service.UserService;

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
    private final UserService userService;


    @GetMapping("/login-view")
    public String loginView() {
        return "user/login";
    }
    
    // 로그인 API
    @PostMapping("/login")
    @ResponseBody
    public Map<String, String> login(
            @RequestParam("loginId") String loginId,
            @RequestParam("password") String password,
            HttpSession session) {
        
        Map<String, String> resultMap = new HashMap<>();
        
        User user = userService.getUser(loginId, password);
        
        if (user != null) {
            // 로그인 성공
            session.setAttribute("loginUser", user);
            session.setAttribute("userId", user.getId());
            session.setAttribute("userNickname", user.getNickname());
            session.setAttribute("favoriteTeamId", user.getFavoriteTeamId());
            resultMap.put("result", "success");
        } else {
            // 로그인 실패
            resultMap.put("result", "fail");
            resultMap.put("message", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        
        return resultMap;
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

        // 세션 정보 보장 (로그인 후 직접 URL 접근 시 대비)
        if (session.getAttribute("userNickname") == null) {
            session.setAttribute("userId", user.getId());
            session.setAttribute("userNickname", user.getNickname());
            session.setAttribute("favoriteTeamId", user.getFavoriteTeamId());
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
