package org.example.service.inventory;

import org.example.model.inventory.ResourceComponent;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {                                  // hides details about storing //helper interface
    Optional<ResourceComponent> getComponent(String ID);                // returns resource or returns null if not found
    List<ResourceComponent> getAllComponents();                         // returns list of components from database
    List<ResourceComponent> getComponentsByCategory(String category);
    void saveComponent(ResourceComponent component);                    // saves resource in Database

}