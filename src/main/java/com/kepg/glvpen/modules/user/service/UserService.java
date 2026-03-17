package com.kepg.glvpen.modules.user.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
			return true;
		} catch(PersistenceException e) {
			log.error("회원가입 실패 - loginId: {}, error: {}", loginId, e.getMessage());
			return false;
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

	// ID로 유저 조회
	public User findById(Integer userId) {
		return userRepository.findById(userId).orElse(null);
	}
}
