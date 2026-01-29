package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.controller;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomTeamDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service.CustomTeamService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 커스텀 팀 REST API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/custom-teams")
@RequiredArgsConstructor
public class CustomTeamRestController {

    private final CustomTeamService customTeamService;

    /**
     * 팀 생성
     * POST /api/custom-teams
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTeam(
            @RequestBody CustomTeamDTO.CreateRequest request,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("팀 생성 요청 - teamName: {}", request.getTeamName());

            // TODO: 세션에서 userId 가져오기
            Integer userId = 1;

            CustomTeamDTO team = customTeamService.createTeam(userId, request);

            result.put("success", true);
            result.put("data", team);
            result.put("message", "팀이 생성되었습니다.");

            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            log.error("팀 생성 실패 - error: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "팀 생성 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 팀 수정
     * PUT /api/custom-teams/{teamId}
     */
    @PutMapping("/{teamId}")
    public ResponseEntity<Map<String, Object>> updateTeam(
            @PathVariable Long teamId,
            @RequestBody CustomTeamDTO.CreateRequest request,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("팀 수정 요청 - teamId: {}, teamName: {}", teamId, request.getTeamName());

            // TODO: 세션에서 userId 가져오기
            Integer userId = 1;

            CustomTeamDTO team = customTeamService.updateTeam(teamId, userId, request);

            result.put("success", true);
            result.put("data", team);
            result.put("message", "팀이 수정되었습니다.");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            log.error("팀 수정 실패 - teamId: {}, error: {}", teamId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "팀 수정 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 팀 조회
     * GET /api/custom-teams/{teamId}
     */
    @GetMapping("/{teamId}")
    public ResponseEntity<Map<String, Object>> getTeam(
            @PathVariable Long teamId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("팀 조회 요청 - teamId: {}", teamId);

            // TODO: 세션에서 userId 가져오기
            Integer userId = 1;

            CustomTeamDTO team = customTeamService.getTeam(teamId, userId);

            result.put("success", true);
            result.put("data", team);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        } catch (Exception e) {
            log.error("팀 조회 실패 - teamId: {}, error: {}", teamId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "팀 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 사용자의 모든 팀 조회
     * GET /api/custom-teams?userId={userId}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserTeams(
            @RequestParam(required = false) Integer userId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            // TODO: 세션에서 userId 가져오기
            if (userId == null) {
                userId = 1;
            }

            log.info("사용자 팀 목록 조회 - userId: {}", userId);

            List<CustomTeamDTO> teams = customTeamService.getUserTeams(userId);

            result.put("success", true);
            result.put("data", teams);
            result.put("count", teams.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("팀 목록 조회 실패 - userId: {}, error: {}", userId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "팀 목록 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 팀 삭제
     * DELETE /api/custom-teams/{teamId}
     */
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Map<String, Object>> deleteTeam(
            @PathVariable Long teamId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("팀 삭제 요청 - teamId: {}", teamId);

            // TODO: 세션에서 userId 가져오기
            Integer userId = 1;

            customTeamService.deleteTeam(teamId, userId);

            result.put("success", true);
            result.put("message", "팀이 삭제되었습니다.");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        } catch (Exception e) {
            log.error("팀 삭제 실패 - teamId: {}, error: {}", teamId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "팀 삭제 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
