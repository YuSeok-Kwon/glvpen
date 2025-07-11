package com.kepg.BaseBallLOCK.modules.team.teamStats.dto;

public interface TeamStatRankingInterface {
    Integer getTeamId();
    String getTeamName();
    String getLogoName();

    Double getOps();
    Double getAvg();
    Double getHr();
    Double getSb();
    Double getBetterWar();
    Double getPitcherWar();
    Double getSo();
    Double getW();
    Double getH();
    Double getSv();
    Double getEra();
    Double getWhip();
    Double getBb();
    Double getBattingWaa();
    Double getBaserunningWaa();
    Double getDefenseWaa();
    Double getStartingWaa();
    Double getBullpenWaa();
}