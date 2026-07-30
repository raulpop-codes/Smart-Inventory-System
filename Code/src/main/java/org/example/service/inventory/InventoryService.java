package org.example.service.inventory;

import org.example.infrastructure.database.TransactionManager;
import org.example.model.inventory.ResourceComponent;
import org.example.service.inventory.exceptions.ResourceNotFoundException;
import org.example.util.AppLogger;
import org.example.util.DeviceUtil;
import org.example.util.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InventoryService {
    private final InventoryRepository repository;
    private final List<InventoryObserver> observers;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
        this.observers = new ArrayList<>();
    }

    /**
     * Registers a new inventory observer to receive inventory change notifications.
     */
    public void registerObserver(InventoryObserver observer) {
        this.observers.add(observer);
    }

    /**
     * Retrieves a specific resource component by its identifier.
     */
    public Optional<ResourceComponent> getComponent(String resourceID) {
        return repository.getComponent(resourceID);
    }

    /**
     * Retrieves all resource components stored in inventory.
     */
    public List<ResourceComponent> getAllComponents() {
        return repository.getAllComponents();
    }

    /**
     * Retrieves components filtered by category, or all components if category is "ALL".
     */
    public List<ResourceComponent> getComponentsByCategory(String category) {
        if ("ALL".equalsIgnoreCase(category)) {
            return repository.getAllComponents();
        }
        return repository.getComponentsByCategory(category);
    }

    /**
     * Adds a specified amount to a resource component, updating storage and notifying observers.
     */
    public void addResource(String resourceID, long amount) {
        String deviceId = DeviceUtil.getDeviceId();

        ResourceComponent component = repository.getComponent(resourceID)
                .orElseThrow(() -> {
                    AppLogger.log(LogLevel.WARN, String.format("[%s] Resource_NotFound: Attempted to add quantity to unknown resource ID: %s", deviceId, resourceID));
                    return new ResourceNotFoundException("Unknown Resource: " + resourceID);
                });

        long oldQuantity = component.getQuantity();
        component.addQuantity(amount);
        repository.saveComponent(component);
        notifyObservers(component);

        AppLogger.log(LogLevel.INVENTORY, String.format("[%s] Resource_Added: Added %d to [%s]. Old quantity: %d, New quantity: %d",
                deviceId, amount, component.getName(), oldQuantity, component.getQuantity()));
    }

    /**
     * Consumes a specified amount from a resource component, updating storage and notifying observers.
     */
    public void consumeResource(String resourceID, long amount) {
        String deviceId = DeviceUtil.getDeviceId();

        ResourceComponent component = repository.getComponent(resourceID)
                .orElseThrow(() -> {
                    AppLogger.log(LogLevel.WARN, String.format("[%s] Resource_NotFound: Attempted to consume quantity from unknown resource ID: %s", deviceId, resourceID));
                    return new ResourceNotFoundException("Unknown Resource: " + resourceID);
                });

        long oldQuantity = component.getQuantity();
        component.subtractQuantity(amount);
        repository.saveComponent(component);
        notifyObservers(component);

        AppLogger.log(LogLevel.INVENTORY, String.format("[%s] Resource_Consumed: Consumed %d from [%s]. Old quantity: %d, New quantity: %d",
                deviceId, amount, component.getName(), oldQuantity, component.getQuantity()));
    }

    /**
     * Queues and notifies all registered observers about inventory change events post-transaction.
     */
    private void notifyObservers(ResourceComponent component) {
        InventoryChangedEvent event = new InventoryChangedEvent(component);
        TransactionManager.queueNotification(() -> {
            observers.forEach(observer -> observer.onInventoryChanged(event));
        });
    }

}