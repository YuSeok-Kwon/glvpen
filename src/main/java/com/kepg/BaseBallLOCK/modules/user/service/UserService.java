package com.kepg.BaseBallLOCK.modules.user.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.common.MD5HashingEncoder;
import com.kepg.BaseBallLOCK.modules.user.domain.User;
import com.kepg.BaseBallLOCK.modules.user.repository.UserRepository;

import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UserService {
	
	private final UserRepository userRepository;
	
	// 로그인
	public User getUser(String loginId, String password) {
		
		String encryptPassword = MD5HashingEncoder.encode(password);
			
		return userRepository.findByLoginIdAndPassword(loginId, encryptPassword);
		
	}
	
	// 회원가입
	public boolean addUser(String loginId, 
			String password, 
			String name, 
			String email, 
			String nickname,
			int favoriteTeamId) {
		
		String encryptPassword = MD5HashingEncoder.encode(password);
		
		User user = User.builder()
				.loginId(loginId)
				.password(encryptPassword)
				.name(name)
				.email(email)
				.nickname(nickname)
				.favoriteTeamId(favoriteTeamId)
				.build();
		
		try {
			userRepository.save(user);
		}
		catch(PersistenceException e) {
			return false;
		}
		return true;
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
	public boolean resetPassword(String loginId, String password) {
		
		Optional<User> optionalUser = userRepository.findByLoginId(loginId);
		
		String encryptPassword = MD5HashingEncoder.encode(password);
		
		if(optionalUser.isPresent()) {
			User user = optionalUser.get();
			
			try {
				user.toBuilder()
				.password(encryptPassword)
				.build();
				
				userRepository.save(user);
				
			} catch(PersistenceException e) {
				return false;
			}
		} else {
			return false;
		}
		return true;
	}
	
	// 모든 유저의 pk_Id정보 찾기
	public List<Integer> findAllUserIds(){
		return userRepository.findAllUserIds();
	}
}
