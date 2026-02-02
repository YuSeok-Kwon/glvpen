package com.kepg.BaseBallLOCK.modules.user.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kepg.BaseBallLOCK.modules.user.domain.User;
import com.kepg.BaseBallLOCK.modules.user.dto.UserHomeDTO;
import com.kepg.BaseBallLOCK.modules.user.service.UserHomeService;
import com.kepg.BaseBallLOCK.modules.user.service.UserService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequestMapping("/user")
@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserHomeService userHomeService;


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

        // 입력값 검증
        if (loginId == null || loginId.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "아이디를 입력해주세요.");
            return resultMap;
        }
        if (password == null || password.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "비밀번호를 입력해주세요.");
            return resultMap;
        }

        User user = userService.getUser(loginId, password);

        if (user != null) {
            // 로그인 성공 - User 객체만 세션에 저장
            session.setAttribute("loginUser", user);
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

    // 아이디 중복 확인 API
    @GetMapping("/duplicate-id")
    @ResponseBody
    public Map<String, Object> checkDuplicateId(@RequestParam("loginId") String loginId) {
        Map<String, Object> resultMap = new HashMap<>();

        // 입력값 검증
        if (loginId == null || loginId.trim().isEmpty()) {
            resultMap.put("result", false);
            resultMap.put("message", "아이디를 입력해주세요.");
            return resultMap;
        }

        // 중복 확인
        boolean isDuplicate = userService.duplicateId(loginId);
        resultMap.put("result", isDuplicate);

        if (isDuplicate) {
            resultMap.put("message", "이미 사용 중인 아이디입니다.");
        } else {
            resultMap.put("message", "사용 가능한 아이디입니다.");
        }

        return resultMap;
    }

    // 닉네임 중복 확인 API
    @GetMapping("/duplicate-nickname")
    @ResponseBody
    public Map<String, Object> checkDuplicateNickname(@RequestParam("nickname") String nickname) {
        Map<String, Object> resultMap = new HashMap<>();

        // 입력값 검증
        if (nickname == null || nickname.trim().isEmpty()) {
            resultMap.put("result", false);
            resultMap.put("message", "닉네임을 입력해주세요.");
            return resultMap;
        }

        // 중복 확인
        boolean isDuplicate = userService.duplicateNickname(nickname);
        resultMap.put("result", isDuplicate);

        if (isDuplicate) {
            resultMap.put("message", "이미 사용 중인 닉네임입니다.");
        } else {
            resultMap.put("message", "사용 가능한 닉네임입니다.");
        }

        return resultMap;
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

        // Service에서 홈 화면 데이터 조회
        UserHomeDTO homeData = userHomeService.getUserHomeData(user);

        // Model에 데이터 추가
        model.addAttribute("myTeam", homeData.getMyTeam());
        model.addAttribute("schedule", homeData.getSchedule());
        model.addAttribute("opponentTeam", homeData.getOpponentTeam());
        model.addAttribute("myRecentResults", homeData.getMyRecentResults());
        model.addAttribute("opponentRecentResults", homeData.getOpponentRecentResults());
        model.addAttribute("myRecord", homeData.getMyRecord());
        model.addAttribute("opponentRecord", homeData.getOpponentRecord());
        model.addAttribute("topHitter", homeData.getTopHitter());
        model.addAttribute("topPitcher", homeData.getTopPitcher());
        model.addAttribute("rankingList", homeData.getRankingList());

        return "user/home";
    }
    
}
