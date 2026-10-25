package org.example.service.inventory;

import org.example.model.inventory.ResourceComponent;
import org.example.service.inventory.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InventoryService against an in-memory repository: no MySQL, no Redis.
 *
 * Observers fire synchronously here, because TransactionManager only defers a
 * notification while a transaction is open, and no test opens one.
 */
class InventoryServiceTest {

    private InMemoryInventoryRepository repository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryInventoryRepository();
        repository.seed(new ResourceComponent("ORO", "Orokin Cell", 10), "Rare");
        repository.seed(new ResourceComponent("PLA", "Plastids", 500), "Common");
        repository.seed(new ResourceComponent("RUB", "Rubedo", 1000), "Common");
        service = new InventoryService(repository);
    }

    @Test
    @DisplayName("adding a resource updates the stock and persists it")
    void addResourceUpdatesAndSaves() {
        service.addResource("PLA", 250);

        assertEquals(750, service.getComponent("PLA").orElseThrow().getQuantity());
        assertEquals(1, repository.saveCount(), "the change has to be written back");
    }

    @Test
    @DisplayName("consuming a resource updates the stock and persists it")
    void consumeResourceUpdatesAndSaves() {
        service.consumeResource("RUB", 400);

        assertEquals(600, service.getComponent("RUB").orElseThrow().getQuantity());
        assertEquals(1, repository.saveCount());
    }

    @Test
    @DisplayName("consuming more than available is refused and nothing is written")
    void consumingMoreThanAvailableIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.consumeResource("ORO", 11));

        assertEquals(10, service.getComponent("ORO").orElseThrow().getQuantity(),
                "stock must survive a refused withdrawal");
        assertEquals(0, repository.saveCount(),
                "a refused operation must not reach the repository");
    }

    @Test
    @DisplayName("an unknown resource id is reported, not silently ignored")
    void unknownResourceThrows() {
        assertThrows(ResourceNotFoundException.class, () -> service.addResource("NOPE", 1));
        assertThrows(ResourceNotFoundException.class, () -> service.consumeResource("NOPE", 1));
        assertEquals(0, repository.saveCount());
    }

    @Test
    @DisplayName("registered observers are told which component changed")
    void observersAreNotifiedWithTheChangedComponent() {
        List<InventoryChangedEvent> seen = new ArrayList<>();
        service.registerObserver(seen::add);

        service.consumeResource("PLA", 100);

        assertEquals(1, seen.size(), "exactly one notification per change");
        assertEquals("PLA", seen.get(0).getComponent().getID());
        assertEquals(400, seen.get(0).getComponent().getQuantity(),
                "the observer must see the quantity after the change, not before");
    }

    @Test
    @DisplayName("a refused change notifies nobody")
    void refusedChangeDoesNotNotify() {
        List<InventoryChangedEvent> seen = new ArrayList<>();
        service.registerObserver(seen::add);

        assertThrows(IllegalArgumentException.class, () -> service.consumeResource("ORO", 999));

        assertTrue(seen.isEmpty(), "observers must not hear about a change that did not happen");
    }

    @Test
    @DisplayName("every registered observer gets the event")
    void allObserversAreNotified() {
        List<InventoryChangedEvent> first = new ArrayList<>();
        List<InventoryChangedEvent> second = new ArrayList<>();
        service.registerObserver(first::add);
        service.registerObserver(second::add);

        service.addResource("RUB", 1);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
    }

    @Test
    @DisplayName("category ALL returns everything, whatever its casing")
    void categoryAllReturnsEverything() {
        assertEquals(3, service.getComponentsByCategory("ALL").size());
        assertEquals(3, service.getComponentsByCategory("all").size());
        assertEquals(3, service.getComponentsByCategory("All").size());
    }

    @Test
    @DisplayName("a real category filters")
    void categoryFilters() {
        List<ResourceComponent> common = service.getComponentsByCategory("Common");

        assertEquals(2, common.size());
        assertTrue(common.stream().allMatch(c -> List.of("PLA", "RUB").contains(c.getID())));
    }

    @Test
    @DisplayName("getAllComponents returns the whole store")
    void getAllComponentsReturnsEverything() {
        assertEquals(3, service.getAllComponents().size());
    }

    @Test
    @DisplayName("an unknown component comes back empty, not null")
    void unknownComponentIsEmptyOptional() {
        assertTrue(service.getComponent("NOPE").isEmpty());
    }
}
