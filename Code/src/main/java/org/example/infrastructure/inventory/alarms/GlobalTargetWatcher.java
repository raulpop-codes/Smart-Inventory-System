package org.example.infrastructure.inventory.alarms;

import org.example.model.inventory.Blueprint;
import org.example.model.inventory.ResourceComponent;
import org.example.service.inventory.BlueprintRepository;
import org.example.service.inventory.InventoryChangedEvent;
import org.example.service.inventory.InventoryObserver;
import org.example.util.AppLogger;
import org.example.util.DeviceUtil;
import org.example.util.LogLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class GlobalTargetWatcher implements InventoryObserver {
    private final BlueprintRepository blueprintRepository;
    private final Consumer<String> onTargetMet;
    private final Set<String> triggeredGlobalTargets = new HashSet<>();

    public GlobalTargetWatcher(BlueprintRepository blueprintRepository, Consumer<String> onTargetMet) {
        this.blueprintRepository = blueprintRepository;
        this.onTargetMet = onTargetMet;
    }

    @Override
    public void onInventoryChanged(InventoryChangedEvent event) {
        String deviceId = DeviceUtil.getDeviceId();
        String updatedResourceID = event.getComponent().getID();
        long currentStock = event.getComponent().getQuantity();
        long totalRequiredAmount = calculateTotalRequiredForResource(updatedResourceID);

        if (totalRequiredAmount == 0) {
            triggeredGlobalTargets.remove(updatedResourceID);
            return;
        }

        if (currentStock >= totalRequiredAmount) {
            if (!triggeredGlobalTargets.contains(updatedResourceID)) {
                triggeredGlobalTargets.add(updatedResourceID);

                String message = String.format("Enough <b>%s</b> for ALL uncrafted blueprints! (%d / %d)",
                        event.getComponent().getName(), currentStock, totalRequiredAmount);

                if (onTargetMet != null) {
                    onTargetMet.accept(message);
                }

                AppLogger.log(LogLevel.INVENTORY, String.format("[%s] GlobalTarget_Met: Target met! Enough [%s] for all Blueprints! (Stock: %d / Total: %d)",
                        deviceId, event.getComponent().getName(), currentStock, totalRequiredAmount));
            }
        } else {
            triggeredGlobalTargets.remove(updatedResourceID);
        }
    }

    private long calculateTotalRequiredForResource(String resourceID) {
        List<Blueprint> blueprints = blueprintRepository.getAllActiveBlueprints();
        long sum = 0;

        for (Blueprint bp : blueprints) {
            if (bp.isUncrafted()) {
                for (ResourceComponent req : bp.getRequirements()) {
                    if (req.getID().equals(resourceID)) {
                        sum += req.getQuantity();
                    }
                }
            }
        }
        return sum;
    }
}