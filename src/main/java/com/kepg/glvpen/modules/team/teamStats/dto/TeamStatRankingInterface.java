package com.kepg.glvpen.modules.team.teamStats.dto;

public interface TeamStatRankingInterface {
    Integer getTeamId();
    String getTeamName();
    String getLogoName();

    Double getOps();
    Double getAvg();
    Double getHr();
    Double getRbi();
    Double getSb();
    Double getH();
    Double getSo();
    Double getW();
    Double getSv();
    Double getHld();
    Double getEra();
    Double getWhip();
    Double getBb();
}
