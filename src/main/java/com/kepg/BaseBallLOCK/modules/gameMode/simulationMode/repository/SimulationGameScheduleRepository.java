package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.SimulationGameSchedule;

public interface SimulationGameScheduleRepository extends JpaRepository<SimulationGameSchedule, Integer> {

}
