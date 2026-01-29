package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service.CustomPlayerService;
import com.kepg.BaseBallLOCK.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom Player RESTful API Controller
 * - /api/custom-players 경로의 RESTful API 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/custom-players")
@RequiredArgsConstructor
public class CustomPlayerRestController {

    private final CustomPlayerService customPlayerService;

    /**
     * 커스텀 선수 목록 조회
     * GET /api/custom-players?mode=HITTER
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCustomPlayers(
            @RequestParam(required = false) String mode,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            List<CustomPlayer> players;

            if (mode != null && !mode.isEmpty()) {
                // 모드별 필터링
                players = customPlayerService.getCustomPlayersByMode(user.getId(), mode);
            } else {
                // 전체 조회
                players = customPlayerService.getActiveCustomPlayers(user.getId());
            }

            List<CustomPlayerResponseDTO> response = players.stream()
                    .map(CustomPlayerResponseDTO::fromEntity)
                    .toList();

            result.put("success", true);
            result.put("players", response);
            result.put("count", response.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("커스텀 선수 목록 조회 실패 - userId: {}, error: {}",
                    user.getId(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "선수 목록 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 특정 커스텀 선수 조회
     * GET /api/custom-players/{playerId}
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<Map<String, Object>> getCustomPlayer(
            @PathVariable Long playerId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            CustomPlayer player = customPlayerService.getCustomPlayerById(playerId);

            // 권한 확인 (RPG 모드인 경우 본인 선수만 조회 가능)
            if (player.isRpgMode() && !player.getUserId().equals(user.getId())) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }

            CustomPlayerResponseDTO response = CustomPlayerResponseDTO.fromEntity(player);

            result.put("success", true);
            result.put("player", response);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        } catch (Exception e) {
            log.error("커스텀 선수 조회 실패 - playerId: {}, error: {}",
                    playerId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "선수 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 커스텀 선수 생성
     * POST /api/custom-players
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCustomPlayer(
            @RequestBody CustomPlayerRequestDTO.CustomPlayerInfo playerInfo,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            CustomPlayer player = customPlayerService.createCustomPlayer(user.getId(), playerInfo);
            CustomPlayerResponseDTO response = CustomPlayerResponseDTO.fromEntity(player);

            result.put("success", true);
            result.put("player", response);
            result.put("message", "커스텀 선수가 생성되었습니다.");

            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (IllegalArgumentException e) {
            log.warn("커스텀 선수 생성 실패 - 잘못된 입력값 - userId: {}, error: {}",
                    user.getId(), e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            log.error("커스텀 선수 생성 실패 - userId: {}, error: {}",
                    user.getId(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "선수 생성 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 커스텀 선수 정보 수정
     * PUT /api/custom-players/{playerId}
     */
    @PutMapping("/{playerId}")
    public ResponseEntity<Map<String, Object>> updateCustomPlayer(
            @PathVariable Long playerId,
            @RequestBody CustomPlayerRequestDTO.CustomPlayerInfo playerInfo,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            CustomPlayer existingPlayer = customPlayerService.getCustomPlayerById(playerId);

            // 권한 확인
            if (existingPlayer.isRpgMode() && !existingPlayer.getUserId().equals(user.getId())) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }

            CustomPlayer updatedPlayer = customPlayerService.updateCustomPlayer(playerId, playerInfo);
            CustomPlayerResponseDTO response = CustomPlayerResponseDTO.fromEntity(updatedPlayer);

            result.put("success", true);
            result.put("player", response);
            result.put("message", "선수 정보가 수정되었습니다.");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        } catch (Exception e) {
            log.error("커스텀 선수 수정 실패 - playerId: {}, error: {}",
                    playerId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "선수 수정 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 커스텀 선수 삭제
     * DELETE /api/custom-players/{playerId}
     */
    @DeleteMapping("/{playerId}")
    public ResponseEntity<Map<String, Object>> deleteCustomPlayer(
            @PathVariable Long playerId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            CustomPlayer player = customPlayerService.getCustomPlayerById(playerId);

            // 권한 확인
            if (player.isRpgMode() && !player.getUserId().equals(user.getId())) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }

            customPlayerService.deleteCustomPlayer(playerId);

            result.put("success", true);
            result.put("message", "선수가 삭제되었습니다.");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        } catch (Exception e) {
            log.error("커스텀 선수 삭제 실패 - playerId: {}, error: {}",
                    playerId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "선수 삭제 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 능력치 할당 (레벨업)
     * POST /api/custom-players/{playerId}/allocate
     */
    @PostMapping("/{playerId}/allocate")
    public ResponseEntity<Map<String, Object>> allocateStats(
            @PathVariable Long playerId,
            @RequestBody CustomPlayerRequestDTO.StatAllocation statAllocation,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            CustomPlayer player = customPlayerService.getCustomPlayerById(playerId);

            // 권한 확인
            if (!player.getUserId().equals(user.getId())) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }

            CustomPlayer updatedPlayer = customPlayerService.allocateStats(playerId, statAllocation);
            CustomPlayerResponseDTO response = CustomPlayerResponseDTO.fromEntity(updatedPlayer);

            result.put("success", true);
            result.put("player", response);
            result.put("message", "능력치가 할당되었습니다.");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            log.error("능력치 할당 실패 - playerId: {}, error: {}",
                    playerId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "능력치 할당 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 경기 결과 반영 (경험치/스탯 갱신)
     * POST /api/custom-players/{playerId}/match-result
     */
    @PostMapping("/{playerId}/match-result")
    public ResponseEntity<Map<String, Object>> applyMatchResult(
            @PathVariable Long playerId,
            @RequestBody CustomPlayerResultDTO matchResult,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            CustomPlayer player = customPlayerService.getCustomPlayerById(playerId);

            // 권한 확인
            if (!player.getUserId().equals(user.getId())) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }

            CustomPlayer updatedPlayer = customPlayerService.applyMatchResult(playerId, matchResult);
            CustomPlayerResponseDTO response = CustomPlayerResponseDTO.fromEntity(updatedPlayer);

            result.put("success", true);
            result.put("player", response);
            result.put("message", "경기 결과가 반영되었습니다.");

            // 레벨업 여부 확인
            if (updatedPlayer.getLevel() > player.getLevel()) {
                result.put("levelUp", true);
                result.put("newLevel", updatedPlayer.getLevel());
            }

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            log.error("경기 결과 반영 실패 - playerId: {}, error: {}",
                    playerId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "경기 결과 반영 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 선수 훈련 실행
     * POST /api/custom-players/training
     */
    @PostMapping("/training")
    public ResponseEntity<Map<String, Object>> trainPlayer(
            @RequestBody CustomPlayerRequestDTO.TrainingRequest trainingRequest,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("선수 훈련 요청 - playerId: {}, trainingType: {}, intensity: {}",
                    trainingRequest.getPlayerId(),
                    trainingRequest.getTrainingType(),
                    trainingRequest.getIntensity());

            CustomPlayerResponseDTO.TrainingResult trainingResult =
                    customPlayerService.trainPlayer(trainingRequest);

            result.put("success", true);
            result.put("data", trainingResult);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            log.error("선수 훈련 실패 - playerId: {}, error: {}",
                    trainingRequest.getPlayerId(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "훈련 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 훈련 가능한 선수 목록 조회
     * GET /api/custom-players/trainable?userId={userId}
     */
    @GetMapping("/trainable")
    public ResponseEntity<Map<String, Object>> getTrainablePlayers(
            @RequestParam Integer userId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("훈련 가능 선수 조회 - userId: {}", userId);

            List<CustomPlayer> players = customPlayerService.getTrainablePlayers(userId);
            List<CustomPlayerResponseDTO> playerDTOs = players.stream()
                    .map(CustomPlayerResponseDTO::fromEntity)
                    .collect(java.util.stream.Collectors.toList());

            result.put("success", true);
            result.put("players", playerDTOs);
            result.put("count", playerDTOs.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("훈련 가능 선수 조회 실패 - userId: {}, error: {}",
                    userId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "선수 목록 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 커스텀 선수 게임 실행
     * POST /api/custom-players/play
     */
    @PostMapping("/play")
    public ResponseEntity<Map<String, Object>> playGame(
            @RequestBody CustomPlayerRequestDTO request,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("게임 실행 요청 - userId: {}, gameMode: {}, difficulty: {}",
                    request.getUserId(), request.getGameMode(), request.getDifficulty());

            CustomPlayerResultDTO gameResult = customPlayerService.playCustomPlayerGame(request);

            result.put("success", true);
            result.put("data", gameResult);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            log.error("게임 실행 실패 - request: {}, error: {}",
                    request, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "게임 실행 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
