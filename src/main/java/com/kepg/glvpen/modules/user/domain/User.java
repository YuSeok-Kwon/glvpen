package com.kepg.glvpen.modules.user.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.kepg.glvpen.modules.team.domain.Team;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String loginId;
    private String password;
    private String name;
    private String email;
    private String nickname;

    /**
     * 선호 팀 (다대일 관계)
     * LAZY 로딩으로 N+1 문제 방지
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favoriteTeamId")
    private Team favoriteTeam;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * 비밀번호 변경 메소드
     * 비밀번호 재설정 시에만 사용됩니다.
     *
     * @param password 새로운 암호화된 비밀번호
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 기존 코드 호환성을 위한 favoriteTeamId getter
     * @return 선호 팀의 ID, 팀이 없으면 null
     */
    public Integer getFavoriteTeamId() {
        return favoriteTeam != null ? favoriteTeam.getId() : null;
    }

    /**
     * 선호 팀 설정 메소드
     * @param favoriteTeam 선호 팀 객체
     */
    public void setFavoriteTeam(Team favoriteTeam) {
        this.favoriteTeam = favoriteTeam;
    }

    /**
     * 닉네임 변경 메소드
     * @param nickname 새로운 닉네임
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 이메일 변경 메소드
     * @param email 새로운 이메일
     */
    public void setEmail(String email) {
        this.email = email;
    }
}