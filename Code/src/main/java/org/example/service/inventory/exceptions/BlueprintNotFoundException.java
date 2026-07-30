package org.example.service.inventory.exceptions;

public class BlueprintNotFoundException extends RuntimeException {
    public BlueprintNotFoundException(String message) {
        super(message);
    }
}
