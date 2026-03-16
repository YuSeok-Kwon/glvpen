package com.kepg.glvpen.modules.team.teamHeadToHead.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TeamHeadToHeadDTO {
    private int season;
    private int teamId;
    private int opponentTeamId;
    private int wins;
    private int losses;
    private int draws;
}
