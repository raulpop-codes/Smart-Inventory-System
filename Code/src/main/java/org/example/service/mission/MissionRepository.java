package org.example.service.mission;

import org.example.model.mission.Mission;

import java.util.List;
import java.util.Optional;

public interface MissionRepository {
    Optional<Mission> getMissionBySlot(int slotIndex);
    List<Mission> getAllMissions();
    void saveMission(Mission mission);
    void deleteMissionBySlot(int slotIndex);
}