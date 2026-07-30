package org.example.service.inventory;

public interface InventoryObserver{                         //every class that implements will be anounced about a change
    void onInventoryChanged(InventoryChangedEvent event);   //executed for everyone when an event is created
}
