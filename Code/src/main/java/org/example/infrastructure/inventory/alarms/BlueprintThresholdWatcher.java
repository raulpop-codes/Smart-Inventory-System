package org.example.infrastructure.inventory.alarms;

import lombok.AllArgsConstructor;
import org.example.model.inventory.Blueprint;
import org.example.model.inventory.ResourceComponent;
import org.example.service.inventory.BlueprintRepository;
import org.example.service.inventory.InventoryChangedEvent;
import org.example.service.inventory.InventoryObserver;
import org.example.service.inventory.InventoryRepository;
import org.example.util.AppLogger;
import org.example.util.DeviceUtil;
import org.example.util.LogLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class BlueprintThresholdWatcher implements InventoryObserver {
    private final BlueprintRepository blueprintRepository;
    private final InventoryRepository inventoryRepository;
    private final Consumer<String> onBlueprintReady;

    private final Set<String> triggeredReadyAlarms = new HashSet<>();

    public BlueprintThresholdWatcher(BlueprintRepository blueprintRepository, InventoryRepository inventoryRepository, Consumer<String> onBlueprintReady) {
        this.blueprintRepository = blueprintRepository;
        this.inventoryRepository = inventoryRepository;
        this.onBlueprintReady = onBlueprintReady;
    }

    @Override
    public void onInventoryChanged(InventoryChangedEvent event){
        String updatedResourceID = event.getComponent().getID();
        List<Blueprint> blueprintList = blueprintRepository.getAllActiveBlueprints();

        for(Blueprint bp : blueprintList){
            if(!bp.isUncrafted()){
                continue;
            }
            boolean involvedResource = bp.getRequirements().stream()
                    .anyMatch(req -> req.getID().equals(updatedResourceID));
            if(involvedResource){
                checkAndUpdateReadyStatus(bp);
            }
        }
    }

    private void checkAndUpdateReadyStatus(Blueprint bp){
        String deviceId = DeviceUtil.getDeviceId();
        boolean allRequirementsMet = true;

        for(ResourceComponent req : bp.getRequirements()){
            long currentStock = inventoryRepository.getComponent(req.getID())
                    .map(ResourceComponent::getQuantity)
                    .orElse(0L);
            if(currentStock < req.getQuantity()){
                allRequirementsMet = false;
                break;
            }
        }
        if(allRequirementsMet){
            if(!triggeredReadyAlarms.contains(bp.getID())){
                triggeredReadyAlarms.add(bp.getID());

                String message = "All resources collected for: <b>" + bp.getName() + "</b>!";
                if (onBlueprintReady != null) {
                    onBlueprintReady.accept(message);
                }

                AppLogger.log(LogLevel.FOUNDRY, String.format("[%s] Blueprint_Ready: All resources collected for %s. Ready to assemble.",
                        deviceId, bp.getName()));
            }
        }else{
            triggeredReadyAlarms.remove(bp.getID());
        }
    }
}