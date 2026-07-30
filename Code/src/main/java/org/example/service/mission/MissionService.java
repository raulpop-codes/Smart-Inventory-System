package org.example.service.mission;

import lombok.AllArgsConstructor;
import org.example.infrastructure.database.TransactionManager;
import org.example.model.inventory.ResourceComponent;
import org.example.model.mission.Mission;
import org.example.service.inventory.InventoryService;
import org.example.util.AppLogger;
import org.example.util.DeviceUtil;
import org.example.util.LogLevel;

import java.time.LocalDateTime;
import java.util.*;

@AllArgsConstructor
public class MissionService {

    private final InventoryService inventoryService;
    private final MissionRepository missionRepository;

    private static final int TOTAL_SLOTS = 3;
    private static final int MIN_MINUTES = 5;
    private static final int MAX_MINUTES = 12 * 60;
    private static final Random RANDOM = new Random();

    /**
     * Returns active missions. If some slots are empty, generates new missions.
     */
    public List<Mission> getOrGenerateActiveMissions() {
        List<Mission> existingMissions = missionRepository.getAllMissions();
        Map<Integer, Mission> slotMap = new HashMap<>();

        existingMissions.forEach(mission -> slotMap.put(mission.getSlotIndex(), mission));

        List<Mission> activeMissions = new ArrayList<>();
        for (int slot = 1; slot <= TOTAL_SLOTS; slot++) {
            Mission mission = slotMap.get(slot);
            if (mission == null) {
                mission = generateRandomMission(slot);
                missionRepository.saveMission(mission);
            }
            activeMissions.add(mission);
        }

        return activeMissions;
    }

    /**
     * Starts a mission on a specific slot.
     */
    public void startMission(int slotIndex) {
        String deviceId = DeviceUtil.getDeviceId();

        // Guard: Check if any mission is already in progress
        if (missionRepository.getAllMissions().stream().anyMatch(Mission::isInProgress)) {
            AppLogger.log(LogLevel.WARN, String.format("[%s] Mission_Conflict: Attempted to start mission while another is already in progress.", deviceId));
            throw new IllegalStateException("Another mission is already in progress!");
        }

        Mission mission = getMissionBySlotOrThrow(slotIndex, deviceId);

        mission.startMission();
        missionRepository.saveMission(mission);

        AppLogger.log(LogLevel.MISSION, String.format("[%s] Mission_Started: Started mission [%s] on slot #%d. Duration: %d minutes.",
                deviceId, mission.getName(), slotIndex, mission.getDurationMinutes()));
    }

    /**
     * Cancels an ongoing mission.
     */
    public void cancelMission(int slotIndex) {
        String deviceId = DeviceUtil.getDeviceId();
        Mission mission = getMissionBySlotOrThrow(slotIndex, deviceId);

        mission.cancelMission();
        missionRepository.saveMission(mission);

        AppLogger.log(LogLevel.MISSION, String.format("[%s] Mission_Cancelled: Cancelled mission [%s] on slot #%d.",
                deviceId, mission.getName(), slotIndex));
    }

    /**
     * Claims rewards and adds resources to the user inventory within a secure transaction.
     */
    public void claimRewards(int slotIndex) {
        String deviceId = DeviceUtil.getDeviceId();
        Mission mission = getMissionBySlotOrThrow(slotIndex, deviceId);

        mission.claimRewards(LocalDateTime.now());

        try {
            TransactionManager.startTransaction();

            // 1. Add reward resources to inventory (InventoryService se ocupă de DB și alarme automat!)
            if (mission.getRewardResources() != null) {
                for (ResourceComponent reward : mission.getRewardResources()) {
                    inventoryService.addResource(reward.getID(), reward.getQuantity());
                }
            }

            // 2. Delete completed mission from slot (will auto-regenerate on next getOrGenerateActiveMissions)
            missionRepository.deleteMissionBySlot(slotIndex);

            TransactionManager.commit();

            AppLogger.log(LogLevel.MISSION, String.format("[%s] Mission_Claimed: Claimed rewards for mission [%s] on slot #%d.",
                    deviceId, mission.getName(), slotIndex));

        } catch (Exception e) {
            TransactionManager.rollback();
            AppLogger.logError(String.format("[%s] Mission_ClaimError: Failure to claim rewards for mission slot %d", deviceId, slotIndex), e);
            throw new RuntimeException("Failed to claim rewards: " + e.getMessage(), e);
        }
    }

    // --- HELPER METHODS ---

    private Mission getMissionBySlotOrThrow(int slotIndex, String deviceId) {
        return missionRepository.getMissionBySlot(slotIndex)
                .orElseThrow(() -> {
                    AppLogger.log(LogLevel.WARN, String.format("[%s] Mission_NotFound: Attempted to access non-existent mission on slot %d", deviceId, slotIndex));
                    return new IllegalArgumentException("No mission exists on slot " + slotIndex);
                });
    }

    private Mission generateRandomMission(int slotIndex) {
        int durationMinutes = MIN_MINUTES + RANDOM.nextInt(MAX_MINUTES - MIN_MINUTES + 1);

        String[] missionTypes = {"Extermination", "Survival", "Defense", "Sabotage", "Spy", "Rescue"};
        String[] locations = {"Grineer Galleon", "Corpus Outpost", "Void Tower", "Infested Derelict", "Kuva Fortress"};

        String missionName = missionTypes[RANDOM.nextInt(missionTypes.length)] + " Operation";
        String location = locations[RANDOM.nextInt(locations.length)];

        // Load available resources to allocate as rewards (Folosind InventoryService acum)
        List<ResourceComponent> availableResources = inventoryService.getComponentsByCategory("RESOURCE");
        List<ResourceComponent> rewards = new ArrayList<>();

        if (availableResources != null && !availableResources.isEmpty()) {
            List<ResourceComponent> shuffledResources = new ArrayList<>(availableResources);
            Collections.shuffle(shuffledResources, RANDOM);

            int targetCount = Math.min(2 + RANDOM.nextInt(2), shuffledResources.size()); // 2 or 3 reward resources
            double durationRatio = (double) durationMinutes / MAX_MINUTES;

            for (int i = 0; i < targetCount; i++) {
                ResourceComponent baseResource = shuffledResources.get(i);
                long baseQty = 150 + RANDOM.nextInt(350);
                long finalQty = Math.max(50, (long) (baseQty * (1 + durationRatio * 5)));

                rewards.add(new ResourceComponent(baseResource.getID(), baseResource.getName(), finalQty));
            }
        }

        String generatedId = "MIS-" + UUID.randomUUID().toString().substring(0, 8);

        return new Mission(generatedId, slotIndex, missionName, location, durationMinutes, rewards);
    }
}