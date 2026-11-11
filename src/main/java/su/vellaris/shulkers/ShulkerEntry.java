package su.vellaris.shulkers;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class ShulkerEntry {
    private String id;
    private String name;
    private String material;
    private List<Map<String, Object>> items;

    public ShulkerEntry() {}

    public ShulkerEntry(String id, String name, List<Map<String, Object>> items) {
        this.id = id;
        this.name = name;
        this.items = items;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }
}