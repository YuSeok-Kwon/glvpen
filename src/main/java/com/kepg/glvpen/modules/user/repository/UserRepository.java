package com.kepg.glvpen.modules.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.kepg.glvpen.modules.user.domain.User;

public interface UserRepository extends JpaRepository<User, Integer>{

	// 로그인 ID와 비밀번호로 유저 조회 (로그인 시 사용)
	public User findByLoginIdAndPassword(String loginId, String password);
	
	// 해당 로그인 ID가 이미 존재하는지 개수 조회 (중복 체크용)
	public int countByLoginId(String loginId);
	
	// 해당 닉네임이 이미 존재하는지 개수 조회 (중복 체크용)
	public int countByNickname(String nickname);
	
	// 이름과 이메일로 유저 조회 (비밀번호 찾기 등 본인 확인용)
	public Optional<User> findByNameAndEmail(String name, String email);
	
	// 로그인 ID, 이름, 이메일로 유저 조회 (ID/PW 찾기 시 사용)
	public User findByLoginIdAndNameAndEmail(String loginId, String name, String email);
	
	// 로그인 ID로 유저 조회 (회원 정보 가져오기 등에서 사용)
	public Optional<User> findByLoginId(String loginId);

	// 닉네임 중복 확인 (본인 제외)
	public int countByNicknameAndIdNot(String nickname, Integer id);

	/**
	 * 모든 유저의 ID 목록 조회
	 * Native Query 대신 JPQL 사용으로 DB 독립성 확보
	 */
	@Query("SELECT u.id FROM User u")
	List<Integer> findAllUserIds();

	/**
	 * 유저 조회 시 선호팀을 함께 로딩 (OSIV 비활성 환경 대응)
	 */
	@Query("SELECT u FROM User u LEFT JOIN FETCH u.favoriteTeam WHERE u.id = :id")
	Optional<User> findByIdWithFavoriteTeam(@org.springframework.data.repository.query.Param("id") Integer id);
}
