package com.kepg.glvpen.modules.gameMode.simulationMode.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.glvpen.modules.gameMode.simulationMode.domain.SimulationGameSchedule;

public interface SimulationGameScheduleRepository extends JpaRepository<SimulationGameSchedule, Integer> {

}
