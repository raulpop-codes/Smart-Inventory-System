package org.example.service.inventory;

import lombok.AllArgsConstructor;
import org.example.model.inventory.Blueprint;
import org.example.model.inventory.ResourceComponent;
import org.example.infrastructure.database.TransactionManager;
import org.example.service.inventory.exceptions.BlueprintNotFoundException;
import org.example.util.AppLogger;
import org.example.util.DeviceUtil;
import org.example.util.LogLevel;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
public class FoundryService {
    private final InventoryService inventoryService;
    private final BlueprintRepository blueprintRepository;

    /**
     * Retrieves all active crafting blueprints available in the foundry.
     */
    public List<Blueprint> getAllActiveBlueprints() {
        return blueprintRepository.getAllActiveBlueprints();
    }

    /**
     * Retrieves blueprints filtered by category.
     */
    public List<Blueprint> getBlueprintsByCategory(String category) {
        return blueprintRepository.getBlueprintsByCategory(category);
    }

    /**
     * Initiates the crafting process for a blueprint by consuming required credits and resource components.
     */
    public void startCrafting(String blueprintID) {
        String deviceId = DeviceUtil.getDeviceId();

        try {
            TransactionManager.startTransaction();

            Blueprint bp = blueprintRepository.getBlueprint(blueprintID)
                    .orElseThrow(() -> {
                        AppLogger.log(LogLevel.WARN, String.format("[%s] Blueprint_NotFound: Attempted to craft non-existent blueprint ID: %s", deviceId, blueprintID));
                        return new BlueprintNotFoundException("Blueprint not found: " + blueprintID);
                    });
            if (bp.isCrafting()) {
                AppLogger.log(LogLevel.WARN, String.format("[%s] Craft_InProgress: Attempted to craft blueprint that is already in progress: %s", deviceId, bp.getName()));
                throw new IllegalArgumentException("Blueprint is already crafting: " + bp.getName());
            }

            if (bp.isCrafted()) {
                AppLogger.log(LogLevel.WARN, String.format("[%s] Craft_AlreadyCompleted: Attempted to craft already completed blueprint: %s", deviceId, bp.getName()));
                throw new IllegalArgumentException("Blueprint already crafted: " + bp.getName());
            }

            inventoryService.consumeResource("Credits", bp.getCreditCost());

            for (ResourceComponent requirement : bp.getRequirements()) {
                inventoryService.consumeResource(requirement.getID(), requirement.getQuantity());
            }

            bp.startCrafting();
            blueprintRepository.saveBlueprint(bp);

            TransactionManager.commit();

            AppLogger.log(LogLevel.FOUNDRY, String.format("[%s] Craft_Started: Started crafting blueprint: [%s]. Required time: %d hours.",
                    deviceId, bp.getName(), bp.getType().getHoursNeeded()));

        } catch (Exception e) {
            TransactionManager.rollback();
            AppLogger.logError(String.format("[%s] Craft_Error: Crafting failed for blueprint ID: %s", deviceId, blueprintID), e);
            throw new RuntimeException("Crafting failed: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels an active crafting process and refunds the consumed credits and resource components back to inventory.
     */
    public void cancelCrafting(String blueprintID) {
        String deviceId = DeviceUtil.getDeviceId();
        try {
            TransactionManager.startTransaction();

            Blueprint bp = blueprintRepository.getBlueprint(blueprintID)
                    .orElseThrow(() -> {
                        AppLogger.log(LogLevel.WARN, String.format("[%s] Blueprint_NotFound: Attempted to cancel non-existent blueprint ID: %s", deviceId, blueprintID));
                        return new BlueprintNotFoundException("Blueprint Not found: " + blueprintID);
                    });

            if (!bp.isCrafting()) {
                AppLogger.log(LogLevel.WARN, String.format("[%s] Craft_NotActive: Attempted to cancel blueprint not in crafting state: %s", deviceId, bp.getName()));
                throw new IllegalStateException("Blueprint is not in crafting state: " + bp.getName());
            }

            bp.cancelCrafting();
            blueprintRepository.saveBlueprint(bp);

            inventoryService.addResource("Credits", bp.getCreditCost());

            for (ResourceComponent requirement : bp.getRequirements()) {
                inventoryService.addResource(requirement.getID(), requirement.getQuantity());
            }

            TransactionManager.commit();
            AppLogger.log(LogLevel.FOUNDRY, String.format("[%s] Craft_Cancelled: Crafting cancelled for: [%s]. Resources returned to inventory.", deviceId, bp.getName()));

        } catch (Exception e) {
            TransactionManager.rollback();
            AppLogger.logError(String.format("[%s] Cancel_Error: Failure to cancel crafting for blueprint ID: %s", deviceId, blueprintID), e);
            throw new RuntimeException("Failure to cancel. Resources were not returned!", e);
        }
    }

    /**
     * Claims a completed crafted item from the foundry and adds the resulting item to inventory.
     */
    public void claimCraftedItem(String blueprintID) {
        String deviceId = DeviceUtil.getDeviceId();
        try {
            TransactionManager.startTransaction();

            Blueprint bp = blueprintRepository.getBlueprint(blueprintID)
                    .orElseThrow(() -> {
                        AppLogger.log(LogLevel.WARN, String.format("[%s] Blueprint_NotFound: Attempted to claim non-existent blueprint ID: %s", deviceId, blueprintID));
                        return new BlueprintNotFoundException("Blueprint Not found: " + blueprintID);
                    });

            bp.completeCrafting(LocalDateTime.now());
            blueprintRepository.saveBlueprint(bp);

            inventoryService.addResource(bp.getResultItemId(), 1);

            TransactionManager.commit();
            AppLogger.log(LogLevel.FOUNDRY, String.format("[%s] Craft_Claimed: Claimed crafted item from foundry: [%s] (Result ID: %s)",
                    deviceId, bp.getName(), bp.getResultItemId()));

        } catch (Exception e) {
            TransactionManager.rollback();
            AppLogger.logError(String.format("[%s] Claim_Error: Failure to claim crafted item for blueprint ID: %s", deviceId, blueprintID), e);
            throw new RuntimeException("Failure to pick up: " + e.getMessage(), e);
        }
    }
}