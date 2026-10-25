package org.example.service.inventory;

import org.example.model.inventory.ResourceComponent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An in-memory stand-in for the MySQL repository.
 *
 * This is what the Repository pattern buys: InventoryService talks to the
 * InventoryRepository interface, so the service layer can be tested with no
 * database, no Redis and no Docker anywhere in sight.
 */
class InMemoryInventoryRepository implements InventoryRepository {

    private final Map<String, ResourceComponent> components = new LinkedHashMap<>();
    private final Map<String, String> categories = new HashMap<>();
    private int saveCount = 0;

    /** Puts a component in the store, under a category, before the test runs. */
    void seed(ResourceComponent component, String category) {
        components.put(component.getID(), component);
        categories.put(component.getID(), category);
    }

    /** How many times the service asked for a write -- lets a test assert it did not. */
    int saveCount() {
        return saveCount;
    }

    @Override
    public Optional<ResourceComponent> getComponent(String ID) {
        return Optional.ofNullable(components.get(ID));
    }

    @Override
    public List<ResourceComponent> getAllComponents() {
        return new ArrayList<>(components.values());
    }

    @Override
    public List<ResourceComponent> getComponentsByCategory(String category) {
        return components.values().stream()
                .filter(c -> category.equals(categories.get(c.getID())))
                .collect(Collectors.toList());
    }

    @Override
    public void saveComponent(ResourceComponent component) {
        components.put(component.getID(), component);
        saveCount++;
    }
}
