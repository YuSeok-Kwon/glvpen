package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomTeam;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomTeamDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository.CustomPlayerRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository.CustomTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 커스텀 팀 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomTeamService {

    private final CustomTeamRepository customTeamRepository;
    private final CustomPlayerRepository customPlayerRepository;
    private final ObjectMapper objectMapper;

    /**
     * 팀 생성
     */
    public CustomTeamDTO createTeam(Integer userId, CustomTeamDTO.CreateRequest request) {
        // 라인업 검증
        validateLineup(request.getLineup());

        // 라인업 데이터를 JSON으로 변환
        String lineupJson = convertLineupToJson(request.getLineup());

        CustomTeam team = CustomTeam.builder()
                .userId(userId)
                .teamName(request.getTeamName())
                .description(request.getDescription())
                .lineupData(lineupJson)
                .build();

        CustomTeam savedTeam = customTeamRepository.save(team);
        log.info("팀 생성 완료 - userId: {}, teamName: {}", userId, request.getTeamName());

        return convertToDTO(savedTeam);
    }

    /**
     * 팀 수정
     */
    public CustomTeamDTO updateTeam(Long teamId, Integer userId, CustomTeamDTO.CreateRequest request) {
        CustomTeam team = customTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));

        // 라인업 검증
        validateLineup(request.getLineup());

        // 라인업 데이터를 JSON으로 변환
        String lineupJson = convertLineupToJson(request.getLineup());

        team.setTeamName(request.getTeamName());
        team.setDescription(request.getDescription());
        team.setLineupData(lineupJson);
        team.setUpdatedAt(LocalDateTime.now());

        CustomTeam updatedTeam = customTeamRepository.save(team);
        log.info("팀 수정 완료 - teamId: {}, teamName: {}", teamId, request.getTeamName());

        return convertToDTO(updatedTeam);
    }

    /**
     * 팀 조회
     */
    @Transactional(readOnly = true)
    public CustomTeamDTO getTeam(Long teamId, Integer userId) {
        CustomTeam team = customTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));

        return convertToDTO(team);
    }

    /**
     * 사용자의 모든 팀 조회
     */
    @Transactional(readOnly = true)
    public List<CustomTeamDTO> getUserTeams(Integer userId) {
        List<CustomTeam> teams = customTeamRepository.findByUserId(userId);
        return teams.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 팀 삭제
     */
    public void deleteTeam(Long teamId, Integer userId) {
        CustomTeam team = customTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));

        customTeamRepository.delete(team);
        log.info("팀 삭제 완료 - teamId: {}, teamName: {}", teamId, team.getTeamName());
    }

    /**
     * 라인업 검증
     */
    private void validateLineup(List<CustomTeamDTO.LineupSlot> lineup) {
        if (lineup == null || lineup.isEmpty()) {
            return; // 빈 라인업 허용
        }

        // 9명 이하 확인
        if (lineup.size() > 9) {
            throw new IllegalArgumentException("라인업은 최대 9명까지 가능합니다.");
        }

        // 타순 중복 확인
        List<Integer> orders = lineup.stream()
                .map(CustomTeamDTO.LineupSlot::getBattingOrder)
                .collect(Collectors.toList());

        if (orders.size() != orders.stream().distinct().count()) {
            throw new IllegalArgumentException("타순이 중복되었습니다.");
        }

        // 선수 ID 중복 확인
        List<Long> playerIds = lineup.stream()
                .map(CustomTeamDTO.LineupSlot::getPlayerId)
                .collect(Collectors.toList());

        if (playerIds.size() != playerIds.stream().distinct().count()) {
            throw new IllegalArgumentException("같은 선수를 중복해서 배치할 수 없습니다.");
        }

        // 포지션 검증
        List<String> validPositions = List.of("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF");
        for (CustomTeamDTO.LineupSlot slot : lineup) {
            if (!validPositions.contains(slot.getPosition())) {
                throw new IllegalArgumentException("유효하지 않은 포지션입니다: " + slot.getPosition());
            }
        }
    }

    /**
     * 라인업을 JSON으로 변환
     */
    private String convertLineupToJson(List<CustomTeamDTO.LineupSlot> lineup) {
        try {
            return objectMapper.writeValueAsString(lineup);
        } catch (JsonProcessingException e) {
            log.error("라인업 JSON 변환 실패", e);
            throw new RuntimeException("라인업 저장에 실패했습니다.");
        }
    }

    /**
     * JSON을 라인업으로 변환
     */
    private List<CustomTeamDTO.LineupSlot> convertJsonToLineup(String lineupJson) {
        if (lineupJson == null || lineupJson.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(lineupJson, new TypeReference<List<CustomTeamDTO.LineupSlot>>() {});
        } catch (JsonProcessingException e) {
            log.error("라인업 JSON 파싱 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * Entity를 DTO로 변환
     */
    private CustomTeamDTO convertToDTO(CustomTeam team) {
        // 라인업 데이터 파싱
        List<CustomTeamDTO.LineupSlot> lineupSlots = convertJsonToLineup(team.getLineupData());

        // 라인업에 선수 정보 추가
        List<CustomTeamDTO.LineupPosition> lineupPositions = new ArrayList<>();

        for (CustomTeamDTO.LineupSlot slot : lineupSlots) {
            CustomPlayer player = customPlayerRepository.findById(slot.getPlayerId())
                    .orElse(null);

            if (player != null) {
                CustomTeamDTO.LineupPosition position = CustomTeamDTO.LineupPosition.builder()
                        .battingOrder(slot.getBattingOrder())
                        .position(slot.getPosition())
                        .playerId(player.getId())
                        .playerName(player.getPlayerName() != null ? player.getPlayerName() : player.getName())
                        .level(player.getLevel())
                        .power(player.getPower())
                        .contact(player.getContact())
                        .speed(player.getSpeed())
                        .fielding(player.getFielding())
                        .arm(player.getArm())
                        .build();

                lineupPositions.add(position);
            }
        }

        return CustomTeamDTO.fromEntity(team, lineupPositions);
    }
}
