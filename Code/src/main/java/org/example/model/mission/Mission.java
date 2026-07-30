package org.example.model.mission;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.model.enums.MissionState;
import org.example.model.inventory.ResourceComponent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class Mission {
    private final String ID;
    private final int slotIndex; // 1 - 3
    private final String name;
    private final String location;
    private final int durationMinutes;
    private final List<ResourceComponent> rewardResources;
    private MissionState state;
    private LocalDateTime finishedAt;

    public Mission(String ID, int slotIndex, String name, String location, int durationMinutes, List<ResourceComponent> rewardResources) {
        if (slotIndex < 1 || slotIndex > 3) {
            throw new IllegalArgumentException("Slot index must be between 1 and 3!");
        }
        this.ID = ID;
        this.slotIndex = slotIndex;
        this.name = name;
        this.location = location;
        this.durationMinutes = Math.max(1, durationMinutes);
        this.rewardResources = rewardResources;
        this.state = MissionState.READY;
        this.finishedAt = null;
    }

    public void startMission() {
        if (this.state != MissionState.READY) {
            throw new IllegalStateException("Mission state must be READY!");
        }
        this.state = MissionState.IN_PROGRESS;
        this.finishedAt = LocalDateTime.now().plusMinutes(this.durationMinutes);
    }

    public void cancelMission() {
        if (this.state != MissionState.IN_PROGRESS) {
            throw new IllegalStateException("Mission state must be IN_PROGRESS!");
        }
        this.state = MissionState.READY;
        this.finishedAt = null;
    }

    public void claimRewards(LocalDateTime now) {
        if (this.state != MissionState.IN_PROGRESS) {
            throw new IllegalStateException("Mission state must be IN_PROGRESS!");
        }

        if (now.isBefore(this.finishedAt)) {
            throw new IllegalArgumentException("Time must be elapsed before mission can be completed. " +
                    "Required time for completion: " + getRemainingTimeString(now));
        }

        this.state = MissionState.COMPLETED;
    }

    public String getRemainingTimeString(LocalDateTime now) {
        if (this.state != MissionState.IN_PROGRESS || this.finishedAt == null) {
            return "N/A";
        }

        Duration duration = Duration.between(now, this.finishedAt);
        if (duration.isNegative() || duration.isZero()) {
            return "Mission Complete!";
        }

        return String.format("%dh %dm %ds", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    public boolean isInProgress() { return this.state == MissionState.IN_PROGRESS; }
    public boolean isReadyToClaim(LocalDateTime now) {
        return (this.state == MissionState.COMPLETED) ||
                (this.state == MissionState.IN_PROGRESS && this.finishedAt != null && now.isAfter(this.finishedAt));
    }
}