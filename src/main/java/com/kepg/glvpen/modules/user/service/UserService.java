package com.kepg.glvpen.modules.user.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.gameMode.simulationMode.domain.PlayerCardOverall;
import com.kepg.glvpen.modules.gameMode.simulationMode.domain.UserCard;
import com.kepg.glvpen.modules.gameMode.simulationMode.domain.UserLineup;
import com.kepg.glvpen.modules.gameMode.simulationMode.repository.PlayerCardOverallRepository;
import com.kepg.glvpen.modules.gameMode.simulationMode.repository.UserCardRepository;
import com.kepg.glvpen.modules.gameMode.simulationMode.repository.UserLineupRepository;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.team.domain.Team;
import com.kepg.glvpen.modules.team.repository.TeamRepository;
import com.kepg.glvpen.modules.user.domain.User;
import com.kepg.glvpen.modules.user.repository.UserRepository;

import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

	private final UserRepository userRepository;
	private final TeamRepository teamRepository;
	private final PasswordEncoder passwordEncoder;
	private final PlayerCardOverallRepository playerCardOverallRepository;
	private final UserCardRepository userCardRepository;
	private final UserLineupRepository userLineupRepository;
	private final BatterStatsRepository batterStatsRepository;

	// 로그인
	public User getUser(String loginId, String password) {
		Optional<User> optionalUser = userRepository.findByLoginId(loginId);

		if (optionalUser.isPresent()) {
			User user = optionalUser.get();
			// BCrypt는 matches 메소드로 비밀번호 검증
			if (passwordEncoder.matches(password, user.getPassword())) {
				return user;
			}
		}

		return null;
	}

	// 회원가입
	@Transactional
	public boolean addUser(String loginId,
			String password,
			String name,
			String email,
			String nickname,
			Integer favoriteTeamId) {

		// BCrypt를 사용한 비밀번호 암호화
		String encryptPassword = passwordEncoder.encode(password);

		// 선호 팀 조회
		Team favoriteTeam = null;
		if (favoriteTeamId != null) {
			favoriteTeam = teamRepository.findById(favoriteTeamId).orElse(null);
		}

		User user = User.builder()
				.loginId(loginId)
				.password(encryptPassword)
				.name(name)
				.email(email)
				.nickname(nickname)
				.favoriteTeam(favoriteTeam)
				.build();

		try {
			userRepository.save(user);
			createInitialCardsAndLineup(user);
			return true;
		} catch(PersistenceException e) {
			log.error("회원가입 실패 - loginId: {}, error: {}", loginId, e.getMessage());
			return false;
		}
	}

	// 회원가입 시 선호팀 기반 초기 카드 + 라인업 자동 생성
	private void createInitialCardsAndLineup(User user) {
		if (user.getFavoriteTeam() == null) return;

		try {
			int teamId = user.getFavoriteTeam().getId();
			int season = 2025;

			// 팀 C/D 등급 카드 조회 (랜덤 순서)
			List<PlayerCardOverall> teamCards = playerCardOverallRepository.findTeamLowGradeCards(teamId, season);

			List<PlayerCardOverall> batterCards = teamCards.stream()
					.filter(c -> "BATTER".equals(c.getType())).toList();
			List<PlayerCardOverall> pitcherCards = teamCards.stream()
					.filter(c -> "PITCHER".equals(c.getType())).toList();

			// 포지션별 타자 배치 (9명)
			String[] positions = {"C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH"};
			Map<String, PlayerCardOverall> lineup = new LinkedHashMap<>();
			Set<Integer> usedPlayers = new HashSet<>();

			// 1차: 팀 카드에서 해당 포지션 선수 배치
			for (PlayerCardOverall card : batterCards) {
				String pos = batterStatsRepository.findPositionByPlayerIdAndSeason(card.getPlayerId(), season);
				if (pos == null || "P".equals(pos) || usedPlayers.contains(card.getPlayerId())) continue;
				if (!lineup.containsKey(pos)) {
					lineup.put(pos, card);
					usedPlayers.add(card.getPlayerId());
				}
			}

			// 2차: 빈 포지션은 팀 내 남은 타자로 채우기
			for (String pos : positions) {
				if (lineup.containsKey(pos)) continue;
				for (PlayerCardOverall card : batterCards) {
					if (usedPlayers.contains(card.getPlayerId())) continue;
					lineup.put(pos, card);
					usedPlayers.add(card.getPlayerId());
					break;
				}
			}

			// 3차: 여전히 부족하면 전체 C/D 카드에서 채우기
			if (lineup.size() < 9) {
				List<PlayerCardOverall> allCards = playerCardOverallRepository.findAllLowGradeCards(season);
				for (String pos : positions) {
					if (lineup.containsKey(pos)) continue;
					for (PlayerCardOverall card : allCards) {
						if (!"BATTER".equals(card.getType()) || usedPlayers.contains(card.getPlayerId())) continue;
						lineup.put(pos, card);
						usedPlayers.add(card.getPlayerId());
						break;
					}
				}
			}

			// 투수 선택
			PlayerCardOverall pitcher = pitcherCards.isEmpty() ? null : pitcherCards.get(0);
			if (pitcher == null) {
				List<PlayerCardOverall> allCards = playerCardOverallRepository.findAllLowGradeCards(season);
				pitcher = allCards.stream()
						.filter(c -> "PITCHER".equals(c.getType()))
						.findFirst().orElse(null);
			}

			// UserCard + UserLineup 일괄 생성
			Timestamp now = Timestamp.from(Instant.now());
			int order = 1;

			for (Map.Entry<String, PlayerCardOverall> entry : lineup.entrySet()) {
				PlayerCardOverall card = entry.getValue();
				String pos = entry.getKey();

				userCardRepository.save(UserCard.builder()
						.userId(user.getId())
						.playerId(card.getPlayerId())
						.season(season)
						.grade(card.getGrade())
						.position(pos)
						.createdAt(now)
						.build());

				userLineupRepository.save(UserLineup.builder()
						.userId(user.getId())
						.playerId(card.getPlayerId())
						.position(pos)
						.orderNum(order++)
						.season(season)
						.build());
			}

			// 투수 카드 + 라인업
			if (pitcher != null) {
				userCardRepository.save(UserCard.builder()
						.userId(user.getId())
						.playerId(pitcher.getPlayerId())
						.season(season)
						.grade(pitcher.getGrade())
						.position("P")
						.createdAt(now)
						.build());

				userLineupRepository.save(UserLineup.builder()
						.userId(user.getId())
						.playerId(pitcher.getPlayerId())
						.position("P")
						.orderNum(10)
						.season(season)
						.build());
			}

			log.info("초기 라인업 생성 완료 - userId: {}, 타자: {}명, 투수: {}",
					user.getId(), lineup.size(), pitcher != null ? "배정" : "없음");

		} catch (Exception e) {
			log.error("초기 라인업 생성 실패 - userId: {}", user.getId(), e);
		}
	}

	// id 중복확인
	public boolean duplicateId(String loginId) {
		if(userRepository.countByLoginId(loginId) > 0) {
			return true;
		} else {
			return false;
		}
	}

	// 닉네임 중복확인
	public boolean duplicateNickname(String nickname) {
		if(userRepository.countByNickname(nickname) > 0) {
			return true;
		} else {
			return false;
		}
	}

	// id찾기
	public User findLoginId(String name, String email) {
		Optional<User> optionalUser = userRepository.findByNameAndEmail(name, email);

		if(optionalUser.isPresent()) {
			User user = optionalUser.get();

			return user;
		} else {
			return null;
		}
	}

	// 비밀번호 찾기 위한 정보일치 확인
	public boolean findUser(String loginId, String name, String email) {

		return userRepository.findByLoginIdAndNameAndEmail(loginId, name, email) != null;
	}

	// 비밀번호 재설정
	@Transactional
	public boolean resetPassword(String loginId, String password) {

		Optional<User> optionalUser = userRepository.findByLoginId(loginId);

		// BCrypt를 사용한 비밀번호 암호화
		String encryptPassword = passwordEncoder.encode(password);

		if(optionalUser.isPresent()) {
			User user = optionalUser.get();

			try {
				// 버그 수정: setPassword로 직접 변경
				user.setPassword(encryptPassword);
				userRepository.save(user);
				return true;
			} catch(PersistenceException e) {
				log.error("비밀번호 변경 실패 - loginId: {}, error: {}", loginId, e.getMessage());
				return false;
			}
		} else {
			log.warn("비밀번호 재설정 실패 - 사용자를 찾을 수 없음: {}", loginId);
			return false;
		}
	}

	// 모든 유저의 pk_Id정보 찾기
	public List<Integer> findAllUserIds(){
		return userRepository.findAllUserIds();
	}

	// 닉네임 변경
	@Transactional
	public boolean updateNickname(Integer userId, String newNickname) {
		// 본인 제외 닉네임 중복 체크
		if (userRepository.countByNicknameAndIdNot(newNickname, userId) > 0) {
			return false;
		}

		Optional<User> optionalUser = userRepository.findById(userId);
		if (optionalUser.isPresent()) {
			User user = optionalUser.get();
			user.setNickname(newNickname);
			userRepository.save(user);
			return true;
		}
		return false;
	}

	// 이메일 변경
	@Transactional
	public boolean updateEmail(Integer userId, String newEmail) {
		Optional<User> optionalUser = userRepository.findById(userId);
		if (optionalUser.isPresent()) {
			User user = optionalUser.get();
			user.setEmail(newEmail);
			userRepository.save(user);
			return true;
		}
		return false;
	}

	// 선호팀 변경
	@Transactional
	public boolean updateFavoriteTeam(Integer userId, Integer teamId) {
		Optional<User> optionalUser = userRepository.findById(userId);
		if (optionalUser.isPresent()) {
			User user = optionalUser.get();
			Team team = teamRepository.findById(teamId).orElse(null);
			if (team == null) {
				return false;
			}
			user.setFavoriteTeam(team);
			userRepository.save(user);
			return true;
		}
		return false;
	}

	// 비밀번호 변경 (현재 비밀번호 확인 후)
	@Transactional
	public boolean changePassword(Integer userId, String currentPassword, String newPassword) {
		Optional<User> optionalUser = userRepository.findById(userId);
		if (optionalUser.isPresent()) {
			User user = optionalUser.get();
			// 현재 비밀번호 검증
			if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
				return false;
			}
			user.setPassword(passwordEncoder.encode(newPassword));
			userRepository.save(user);
			return true;
		}
		return false;
	}

	// 회원 탈퇴
	@Transactional
	public boolean deleteUser(Integer userId, String password) {
		Optional<User> optionalUser = userRepository.findById(userId);
		if (optionalUser.isPresent()) {
			User user = optionalUser.get();
			// 비밀번호 확인
			if (!passwordEncoder.matches(password, user.getPassword())) {
				return false;
			}
			userRepository.delete(user);
			return true;
		}
		return false;
	}

	// 비밀번호 검증 (락커룸 진입용)
	public boolean verifyPassword(Integer userId, String password) {
		Optional<User> optionalUser = userRepository.findById(userId);
		if (optionalUser.isPresent()) {
			return passwordEncoder.matches(password, optionalUser.get().getPassword());
		}
		return false;
	}

	// ID로 유저 조회
	public User findById(Integer userId) {
		return userRepository.findById(userId).orElse(null);
	}

	// ID로 유저 조회 (선호팀 포함 - OSIV 비활성 환경 대응)
	public User findByIdWithFavoriteTeam(Integer userId) {
		return userRepository.findByIdWithFavoriteTeam(userId).orElse(null);
	}
}
