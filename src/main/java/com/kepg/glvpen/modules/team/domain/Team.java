package com.kepg.glvpen.modules.team.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 30, nullable = false)
    private String name;

    @Column(length = 30)
    private String location;

    @Column(length = 30)
    private String stadium;

    private Integer foundationYear;

    @Column(length = 20)
    private String color;
    
    private String logoName;
}