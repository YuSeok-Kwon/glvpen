package com.kepg.BaseBallLOCK.modules.team.teamStats.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 
@AllArgsConstructor 
public class TeamStatsDTO {

	    private int id;
	    private int teamId;
	    private int season;
	    private String category;
	    private double value;          
	    private String rank;               
	
}
