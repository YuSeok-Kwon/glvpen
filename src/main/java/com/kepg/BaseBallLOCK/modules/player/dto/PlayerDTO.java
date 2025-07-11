package com.kepg.BaseBallLOCK.modules.player.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data 
@NoArgsConstructor 
@AllArgsConstructor 
public class PlayerDTO {
    private int id;
    private int teamId;
    private String name;
}
