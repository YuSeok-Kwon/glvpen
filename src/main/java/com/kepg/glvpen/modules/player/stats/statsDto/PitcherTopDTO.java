package com.kepg.glvpen.modules.player.stats.statsDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter @Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PitcherTopDTO {
    private String category;
    private String playerName;
    private String teamName;
    private String logoName;
    private Double value; 
    
    
}