package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.projection;

public interface PlayerCardOverallProjection {
    Integer getId();
    Integer getPlayerId();
    Integer getSeason();
    String getPlayerName();
    String getPosition();
    String getType();
    Integer getOverall();
    String getGrade();

    // 타자용
    Integer getPower();
    Integer getContact();
    Integer getDiscipline();
    Integer getSpeed();

    // 투수용
    Integer getControl();
    Integer getStuff();
    Integer getStamina();
}