package org.example.model.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceComponent is the one place where stock levels are allowed to change,
 * so every guard it enforces is tested here: no negative stock, no zero-amount
 * operations, and no spending more than is available.
 */
class ResourceComponentTest {

    @Test
    @DisplayName("a component cannot be created with a negative quantity")
    void rejectsNegativeInitialQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceComponent("ORO", "Orokin Cell", -1));
    }

    @Test
    @DisplayName("zero is a valid starting quantity")
    void acceptsZeroInitialQuantity() {
        ResourceComponent c = new ResourceComponent("ORO", "Orokin Cell", 0);
        assertEquals(0, c.getQuantity());
        assertEquals("ORO", c.getID());
        assertEquals("Orokin Cell", c.getName());
    }

    @Test
    @DisplayName("adding increases the quantity")
    void addIncreasesQuantity() {
        ResourceComponent c = new ResourceComponent("PLA", "Plastids", 500);
        c.addQuantity(250);
        assertEquals(750, c.getQuantity());
    }

    @Test
    @DisplayName("subtracting decreases the quantity")
    void subtractDecreasesQuantity() {
        ResourceComponent c = new ResourceComponent("PLA", "Plastids", 500);
        c.subtractQuantity(200);
        assertEquals(300, c.getQuantity());
    }

    @Test
    @DisplayName("spending the whole stock is allowed, going past it is not")
    void allowsExactlyTheAvailableAmount() {
        ResourceComponent c = new ResourceComponent("RUB", "Rubedo", 100);
        c.subtractQuantity(100);
        assertEquals(0, c.getQuantity());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> c.subtractQuantity(1));
        assertTrue(e.getMessage().contains("Not enough resources"),
                "the message should say what went wrong: " + e.getMessage());
    }

    @Test
    @DisplayName("a failed subtraction leaves the quantity untouched")
    void failedSubtractionDoesNotMutate() {
        ResourceComponent c = new ResourceComponent("RUB", "Rubedo", 100);
        assertThrows(IllegalArgumentException.class, () -> c.subtractQuantity(101));
        assertEquals(100, c.getQuantity(), "stock must not change when the operation is refused");
    }

    @Test
    @DisplayName("zero and negative amounts are refused on both operations")
    void rejectsZeroAndNegativeAmounts() {
        ResourceComponent c = new ResourceComponent("FER", "Ferrite", 50);
        assertThrows(IllegalArgumentException.class, () -> c.addQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> c.addQuantity(-5));
        assertThrows(IllegalArgumentException.class, () -> c.subtractQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> c.subtractQuantity(-5));
        assertEquals(50, c.getQuantity());
    }
}
