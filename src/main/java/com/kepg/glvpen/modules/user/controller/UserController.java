package com.kepg.glvpen.modules.user.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kepg.glvpen.modules.team.domain.Team;
import com.kepg.glvpen.modules.team.repository.TeamRepository;
import com.kepg.glvpen.modules.user.domain.User;
import com.kepg.glvpen.modules.user.dto.UserHomeDTO;
import com.kepg.glvpen.modules.user.service.UserHomeService;
import com.kepg.glvpen.modules.user.service.UserService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequestMapping("/user")
@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserHomeService userHomeService;
    private final TeamRepository teamRepository;


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

    // 회원가입 API
    @PostMapping("/join")
    @ResponseBody
    public Map<String, String> join(
            @RequestParam("loginId") String loginId,
            @RequestParam("password") String password,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("nickname") String nickname,
            @RequestParam("favoriteTeamId") Integer favoriteTeamId) {

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
        if (name == null || name.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "이름을 입력해주세요.");
            return resultMap;
        }
        if (email == null || email.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "이메일을 입력해주세요.");
            return resultMap;
        }
        if (nickname == null || nickname.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "닉네임을 입력해주세요.");
            return resultMap;
        }

        // 회원가입 처리
        boolean isSuccess = userService.addUser(loginId, password, name, email, nickname, favoriteTeamId);

        if (isSuccess) {
            resultMap.put("result", "success");
            resultMap.put("message", "회원가입이 완료되었습니다.");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "회원가입에 실패했습니다. 다시 시도해주세요.");
        }

        return resultMap;
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
        model.addAttribute("todayAllGames", homeData.getTodayAllGames());
        model.addAttribute("todayFuturesGames", homeData.getTodayFuturesGames());
        model.addAttribute("season", homeData.getSeason());
        model.addAttribute("seriesLabel", homeData.getSeriesLabel());
        model.addAttribute("recentColumns", homeData.getRecentColumns());

        return "user/home";
    }

    // 락커룸 비밀번호 확인 페이지
    @GetMapping("/locker-room")
    public String lockerRoomGate(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return "redirect:/user/login-view";
        }

        // 이미 인증된 경우 바로 락커룸으로
        Boolean verified = (Boolean) session.getAttribute("lockerRoomVerified");
        if (Boolean.TRUE.equals(verified)) {
            return "redirect:/user/locker-room-view";
        }

        return "user/locker-room-gate";
    }

    // 락커룸 비밀번호 검증
    @PostMapping("/locker-room-verify")
    @ResponseBody
    public Map<String, String> verifyLockerRoom(
            @RequestParam("password") String password,
            HttpSession session) {

        Map<String, String> resultMap = new HashMap<>();
        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            resultMap.put("result", "fail");
            resultMap.put("message", "로그인이 필요합니다.");
            return resultMap;
        }

        // DB에서 최신 유저 정보 조회하여 비밀번호 검증
        boolean valid = userService.verifyPassword(user.getId(), password);
        if (valid) {
            session.setAttribute("lockerRoomVerified", true);
            resultMap.put("result", "success");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "비밀번호가 일치하지 않습니다.");
        }
        return resultMap;
    }

    // 락커룸 메인 페이지 (인증 후)
    @GetMapping("/locker-room-view")
    public String lockerRoomView(Model model, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return "redirect:/user/login-view";
        }

        Boolean verified = (Boolean) session.getAttribute("lockerRoomVerified");
        if (!Boolean.TRUE.equals(verified)) {
            return "redirect:/user/locker-room";
        }

        User freshUser = userService.findByIdWithFavoriteTeam(user.getId());
        model.addAttribute("user", freshUser);
        model.addAttribute("teams", teamRepository.findAll());

        return "user/locker-room";
    }

    // 닉네임 변경 API
    @PostMapping("/update-nickname")
    @ResponseBody
    public Map<String, String> updateNickname(
            @RequestParam("nickname") String nickname,
            HttpSession session) {

        Map<String, String> resultMap = new HashMap<>();
        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            resultMap.put("result", "fail");
            resultMap.put("message", "로그인이 필요합니다.");
            return resultMap;
        }

        if (nickname == null || nickname.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "닉네임을 입력해주세요.");
            return resultMap;
        }

        boolean success = userService.updateNickname(user.getId(), nickname);
        if (success) {
            // 세션 유저 갱신
            User updatedUser = userService.findById(user.getId());
            session.setAttribute("loginUser", updatedUser);
            resultMap.put("result", "success");
            resultMap.put("message", "닉네임이 변경되었습니다.");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "이미 사용 중인 닉네임입니다.");
        }
        return resultMap;
    }

    // 이메일 변경 API
    @PostMapping("/update-email")
    @ResponseBody
    public Map<String, String> updateEmail(
            @RequestParam("email") String email,
            HttpSession session) {

        Map<String, String> resultMap = new HashMap<>();
        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            resultMap.put("result", "fail");
            resultMap.put("message", "로그인이 필요합니다.");
            return resultMap;
        }

        if (email == null || email.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "이메일을 입력해주세요.");
            return resultMap;
        }

        boolean success = userService.updateEmail(user.getId(), email);
        if (success) {
            User updatedUser = userService.findById(user.getId());
            session.setAttribute("loginUser", updatedUser);
            resultMap.put("result", "success");
            resultMap.put("message", "이메일이 변경되었습니다.");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "이메일 변경에 실패했습니다.");
        }
        return resultMap;
    }

    // 선호팀 변경 API
    @PostMapping("/update-favorite-team")
    @ResponseBody
    public Map<String, String> updateFavoriteTeam(
            @RequestParam("teamId") Integer teamId,
            HttpSession session) {

        Map<String, String> resultMap = new HashMap<>();
        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            resultMap.put("result", "fail");
            resultMap.put("message", "로그인이 필요합니다.");
            return resultMap;
        }

        boolean success = userService.updateFavoriteTeam(user.getId(), teamId);
        if (success) {
            User updatedUser = userService.findById(user.getId());
            session.setAttribute("loginUser", updatedUser);
            resultMap.put("result", "success");
            resultMap.put("message", "선호팀이 변경되었습니다.");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "선호팀 변경에 실패했습니다.");
        }
        return resultMap;
    }

    // 비밀번호 변경 API
    @PostMapping("/change-password")
    @ResponseBody
    public Map<String, String> changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            HttpSession session) {

        Map<String, String> resultMap = new HashMap<>();
        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            resultMap.put("result", "fail");
            resultMap.put("message", "로그인이 필요합니다.");
            return resultMap;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            resultMap.put("result", "fail");
            resultMap.put("message", "새 비밀번호를 입력해주세요.");
            return resultMap;
        }

        boolean success = userService.changePassword(user.getId(), currentPassword, newPassword);
        if (success) {
            resultMap.put("result", "success");
            resultMap.put("message", "비밀번호가 변경되었습니다. 다시 로그인해주세요.");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "현재 비밀번호가 일치하지 않습니다.");
        }
        return resultMap;
    }

    // 회원 탈퇴 API
    @PostMapping("/delete-account")
    @ResponseBody
    public Map<String, String> deleteAccount(
            @RequestParam("password") String password,
            HttpSession session) {

        Map<String, String> resultMap = new HashMap<>();
        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            resultMap.put("result", "fail");
            resultMap.put("message", "로그인이 필요합니다.");
            return resultMap;
        }

        boolean success = userService.deleteUser(user.getId(), password);
        if (success) {
            session.invalidate();
            resultMap.put("result", "success");
            resultMap.put("message", "회원 탈퇴가 완료되었습니다.");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "비밀번호가 일치하지 않습니다.");
        }
        return resultMap;
    }

}
