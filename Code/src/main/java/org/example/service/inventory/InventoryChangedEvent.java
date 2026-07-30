package org.example.service.inventory;

import lombok.Getter;
import org.example.model.inventory.ResourceComponent;

@Getter
public class InventoryChangedEvent {            //helper class for Observers
    private final ResourceComponent component;  // says what resource changed

    public InventoryChangedEvent(ResourceComponent component) {
        this.component = component;
    }
}

