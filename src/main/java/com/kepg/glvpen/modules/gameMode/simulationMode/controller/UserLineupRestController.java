package com.kepg.glvpen.modules.gameMode.simulationMode.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.modules.gameMode.simulationMode.dto.UserCardViewDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.UserCardService;
import com.kepg.glvpen.modules.gameMode.simulationMode.domain.UserLineup;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.UserLineupDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.UserLineupResponseDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.UserLineupService;
import com.kepg.glvpen.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user-lineups")
@RequiredArgsConstructor
public class UserLineupRestController {

    private final UserLineupService userLineupService;
    private final UserCardService userCardService;

    @GetMapping
    public List<UserLineupResponseDTO> getLineup(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        Integer userId = user != null ? user.getId() : null;
        List<UserLineup> lineups = userLineupService.getLineupByUserId(userId);
        return lineups.stream()
                .map(UserLineupResponseDTO::fromEntity)
                .toList();
    }

    @PostMapping("/save-all")
    public ResponseEntity<Void> saveAllLineup(@RequestBody List<UserLineupDTO> lineupList, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        Integer userId = user != null ? user.getId() : null;

        userLineupService.clearLineup(userId); // 기존 라인업 삭제
        for (UserLineupDTO dto : lineupList) {
            userLineupService.save(dto, userId);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear")
    public ResponseEntity<?> clearLineup(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        Integer userId = user != null ? user.getId() : null;

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        userLineupService.clearLineup(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-cards")
    public List<UserCardViewDTO> getMyCards(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        Integer userId = user != null ? user.getId() : null;
        return userCardService.getUserCardViewList(userId);
    }

    @GetMapping("/my-lineup")
    public ResponseEntity<List<UserLineupDTO>> getMyLineup(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        Integer userId = user != null ? user.getId() : null;
        List<UserLineupDTO> lineup = userLineupService.getUserLineup(userId);
        return ResponseEntity.ok(lineup);
    }
}
