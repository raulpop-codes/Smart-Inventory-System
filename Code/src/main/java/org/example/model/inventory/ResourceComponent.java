package org.example.model.inventory;

import lombok.Getter;

@Getter
public class ResourceComponent {    //Brute resource
    private final String ID;
    private final String name;
    private long quantity;

    public ResourceComponent(String ID  , String name, long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be positive!");
        }
        this.ID = ID;
        this.name = name;
        this.quantity = quantity;
    }

    public void subtractQuantity(long amount) {
        checkValidAmount(amount, true);
        this.quantity -= amount;
    }

    public void addQuantity(long amount) {
        checkValidAmount(amount, false);
        this.quantity += amount;
    }

    private void checkValidAmount(long amount, boolean subtractOperation) {                 //helper function => will only allow
        if (amount <= 0) {                                                                  // positive numbers and valid operations
            throw new IllegalArgumentException("Negative or zero amounts not allowed!");
        }

        if (subtractOperation && (this.quantity - amount < 0)) {
            throw new IllegalArgumentException("Not enough resources! Available: " + this.quantity);
        }
    }
}
