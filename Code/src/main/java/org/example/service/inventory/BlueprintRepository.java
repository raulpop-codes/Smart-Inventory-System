package org.example.service.inventory;

import org.example.model.inventory.Blueprint;

import java.util.List;
import java.util.Optional;

public interface BlueprintRepository {                      // hides details about how a Blueprint is stored or extracted //helper interface
    Optional<Blueprint> getBlueprint(String ID);                // may return null if Blueprint is not found in Database
    List<Blueprint> getAllActiveBlueprints();               // returns a list of Blueprints that are not already crafted
    List<Blueprint> getBlueprintsByCategory(String category);
    void saveBlueprint(Blueprint blueprint);           // Saves state and finish time of Blueprint in Database
}
