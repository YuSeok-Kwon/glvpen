package com.kepg.BaseBallLOCK.modules.game;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerDTO;
import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.team.service.TeamService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RequestMapping("/game")
@Controller
@RequiredArgsConstructor
@Slf4j
public class GameController {

	private final ScheduleService scheduleService;
	private final TeamService teamService;

	@GetMapping("/home-view")
	public String home() {
		return "game/home";
	}

	@GetMapping("/custom-hitter-mode")
	public String customHitterMode() {
		// Custom Hitter Mode: CustomPlayer 컨트롤러로 리다이렉트
		return "redirect:/customPlayer";
	}

	@GetMapping("/real-match-mode")
	public String realMatchMode(Model model) {
		// 어제 날짜 계산
		LocalDate yesterday = LocalDate.now().minusDays(1);
		
		// 월요일인 경우 (일요일 경기가 있었으므로 금요일까지 가서 최근 경기 찾기)
		if (yesterday.getDayOfWeek().getValue() == 1) { // 월요일
			yesterday = yesterday.minusDays(1); // 일요일로
		}
		
		// 어제 경기들 가져오기
		Timestamp startOfDay = Timestamp.valueOf(yesterday.atStartOfDay());
		Timestamp endOfDay = Timestamp.valueOf(yesterday.atTime(23, 59, 59));
		
		List<Schedule> yesterdayGames = scheduleService.getSchedulesByDateRange(startOfDay, endOfDay);
		
		// 날짜 포맷팅
		String formattedDate = yesterday.format(DateTimeFormatter.ofPattern("yy년 MM월 dd일"));
		
		model.addAttribute("yesterdayGames", yesterdayGames);
		model.addAttribute("gameDate", yesterday);
		model.addAttribute("formattedGameDate", formattedDate);
		
		return "game/real-match";
	}

	@GetMapping("/classic-sim-mode")
	public String classicSimMode() {
		// Classic Sim Mode: 카드 보관함, 라인업 구성, 카드 뽑기, 게임 시작 메뉴로 연결
		return "game/classic-sim";
	}

	@PostMapping("/create-custom-player")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> createCustomPlayer(@RequestBody CustomPlayerDTO playerDTO) {
		try {
			// 임시로 로그 출력으로 대체 (추후 서비스 연결 예정)
			log.info("Custom player creation requested: {}", playerDTO.getName());
			// 성공 시, 클라이언트에서 리디렉션할 수 있도록 success 플래그와 메시지를 보냅니다.
			return ResponseEntity.ok(Collections.singletonMap("success", true));
		} catch (Exception e) {
			log.error("커스텀 선수 생성 실패 - name: {}, error: {}",
				playerDTO.getName(), e.getMessage(), e);
			// 실패 시, 에러 메시지를 포함하여 응답합니다.
			return ResponseEntity.badRequest().body(Collections.singletonMap("success", false));
		}
	}
	
}
