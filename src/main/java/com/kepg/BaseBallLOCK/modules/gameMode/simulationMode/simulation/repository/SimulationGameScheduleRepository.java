package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.domain.SimulationGameSchedule;

public interface SimulationGameScheduleRepository extends JpaRepository<SimulationGameSchedule, Integer> {

}
