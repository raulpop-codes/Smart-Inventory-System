package org.example.model.enums;

import lombok.Getter;

@Getter
public enum CraftingType {
    COMPONENT_OR_WEAPON(12),
    WARFRAME(72);

    private final int hoursNeeded;

    CraftingType(int hoursNeeded) {
        this.hoursNeeded = hoursNeeded;
    }
}
