package com.kepg.BaseBallLOCK.modules.review.dto;

import lombok.Data;

@Data
public class ReviewDTO {
    private int scheduleId;      
    private String summary;      
    private String bestPlayer;   
    private String worstPlayer;  
    private String feelings;     
    private int rating;          
}