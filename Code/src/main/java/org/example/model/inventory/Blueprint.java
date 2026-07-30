package org.example.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.model.enums.CraftingState;
import org.example.model.enums.CraftingType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor                                      // Used by MySQLBlueprintRepository
public class Blueprint {
    private final String ID;
    private final String name;
    private final String resultItemId;
    private final long creditCost;
    private final List<ResourceComponent> requirements;
    private final CraftingType type;                        // Weapon/Warframe
    private CraftingState state;                            // UNCRAFTED/CRAFTING/CRAFTED
    private LocalDateTime craftingFinishedAt;               // null by default

    public Blueprint(String ID, String name, String resultItemId, long creditCost, List<ResourceComponent> requirements, CraftingType type) {
        this.ID = ID;
        this.name = name;
        this.resultItemId = resultItemId;
        this.creditCost = Math.max(0, creditCost);
        this.requirements = requirements;
        this.type = type;
        this.state = CraftingState.UNCRAFTED;
        this.craftingFinishedAt = null;
    }

    public void startCrafting() {
        if (this.state != CraftingState.UNCRAFTED) {
            throw new IllegalStateException("Blueprint state must be: uncrafted!");
        }
        this.state = CraftingState.CRAFTING;
        this.craftingFinishedAt = LocalDateTime.now().plusHours(this.type.getHoursNeeded());
    }

    public void cancelCrafting() {
        if (this.state != CraftingState.CRAFTING) {
            throw new IllegalStateException("Blueprint state must be: crafting!");
        }
        this.state = CraftingState.UNCRAFTED;
        this.craftingFinishedAt = null;
    }

    public void completeCrafting(LocalDateTime now) {
        if (this.state != CraftingState.CRAFTING) {
            throw new IllegalStateException("Blueprint state must be: crafting!");
        }

        if (now.isBefore(this.craftingFinishedAt)) {
            throw new IllegalArgumentException("Time must elapse before the item is ready to collect. " +
                    "Required time for completion: " + getRemainingTimeString(now));
        }

        this.state = CraftingState.CRAFTED;
        this.craftingFinishedAt = null;
    }

    public String getRemainingTimeString(LocalDateTime now) {
        if (this.state != CraftingState.CRAFTING || craftingFinishedAt == null) {
            return "N/A";
        }

        java.time.Duration duration = java.time.Duration.between(now, craftingFinishedAt);
        if (duration.isNegative() || duration.isZero()) {
            return "Ready for pickup!";
        }

        return String.format("%dh %dm %ds", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    public boolean isUncrafted() { return this.state == CraftingState.UNCRAFTED; }
    public boolean isCrafting()  { return this.state == CraftingState.CRAFTING; }
    public boolean isCrafted()   { return this.state == CraftingState.CRAFTED; }
}