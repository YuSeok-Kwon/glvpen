package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service.hitter;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository.CustomPlayerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 커스텀 히터 모드 서비스
 * - 간단한 커스텀 선수 생성 및 관리
 */
@Service("customHitterService")
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CustomHitterService {
    
    private final CustomPlayerRepository customPlayerRepository;

    /**
     * 커스텀 히터 생성
     */
    public CustomPlayer createPlayer(CustomPlayerDTO playerDTO) {
        log.info("커스텀 히터 생성 시작: {}", playerDTO.getName());
        
        CustomPlayer player = CustomPlayer.builder()
            .mode("HITTER")
            .name(playerDTO.getName())
            .age(playerDTO.getAge())
            .gender(playerDTO.getGender())
            .skin(playerDTO.getSkin())
            .hair(playerDTO.getHair())
            .batting(playerDTO.getBatting())
            .height(playerDTO.getHeight())
            .weight(playerDTO.getWeight())
            .position(playerDTO.getPosition())
            .avatarUrl(playerDTO.getAvatarUrl())
            .power(playerDTO.getPower() > 0 ? playerDTO.getPower() : 50)
            .contact(playerDTO.getContact() > 0 ? playerDTO.getContact() : 50)
            .speed(playerDTO.getSpeed() > 0 ? playerDTO.getSpeed() : 50)
            .defense(playerDTO.getDefense() > 0 ? playerDTO.getDefense() : 50)
            .build();
        
        CustomPlayer savedPlayer = customPlayerRepository.save(player);
        log.info("커스텀 히터 생성 완료: ID={}, Name={}", savedPlayer.getId(), savedPlayer.getName());
        
        return savedPlayer;
    }

    /**
     * 모든 히터 플레이어 조회
     */
    @Transactional(readOnly = true)
    public List<CustomPlayer> getAllHitterPlayers() {
        return customPlayerRepository.findHitterPlayers();
    }

    /**
     * 포지션별 히터 플레이어 조회
     */
    @Transactional(readOnly = true)
    public List<CustomPlayer> getHitterPlayersByPosition(String position) {
        return customPlayerRepository.findHitterPlayersByPosition(position);
    }

    /**
     * 히터 플레이어 상세 조회
     */
    @Transactional(readOnly = true)
    public CustomPlayer getHitterPlayer(Long id) {
        return customPlayerRepository.findById(id)
            .filter(CustomPlayer::isHitterMode)
            .orElseThrow(() -> new RuntimeException("히터 플레이어를 찾을 수 없습니다: " + id));
    }

    /**
     * 히터 플레이어 업데이트
     */
    public CustomPlayer updateHitterPlayer(Long id, CustomPlayerDTO playerDTO) {
        CustomPlayer player = getHitterPlayer(id);
        
        // 필드 업데이트
        if (playerDTO.getName() != null) player.setName(playerDTO.getName());
        if (playerDTO.getAge() > 0) player.setAge(playerDTO.getAge());
        if (playerDTO.getPosition() != null) player.setPosition(playerDTO.getPosition());
        if (playerDTO.getPower() > 0) player.setPower(playerDTO.getPower());
        if (playerDTO.getContact() > 0) player.setContact(playerDTO.getContact());
        if (playerDTO.getSpeed() > 0) player.setSpeed(playerDTO.getSpeed());
        if (playerDTO.getDefense() > 0) player.setDefense(playerDTO.getDefense());
        
        return customPlayerRepository.save(player);
    }

    /**
     * 히터 플레이어 삭제
     */
    public void deleteHitterPlayer(Long id) {
        CustomPlayer player = getHitterPlayer(id);
        customPlayerRepository.delete(player);
        log.info("히터 플레이어 삭제 완료: ID={}, Name={}", player.getId(), player.getName());
    }
}
