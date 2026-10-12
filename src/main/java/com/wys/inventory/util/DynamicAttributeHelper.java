package com.wys.inventory.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicAttributeHelper {

    public static class AttributeRow {
        private String key;
        private String value;

        public AttributeRow() {}
        public AttributeRow(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static Map<String, Object> toMap(List<AttributeRow> rows) {
        Map<String, Object> map = new HashMap<>();
        if (rows != null) {
            for (AttributeRow row : rows) {
                if (row.getKey() != null && !row.getKey().trim().isEmpty()) {
                    map.put(row.getKey().trim(), row.getValue());
                }
            }
        }
        return map;
    }

    public static List<AttributeRow> toRows(Map<String, Object> map) {
        List<AttributeRow> rows = new ArrayList<>();
        if (map != null) {
            map.forEach((k, v) -> rows.add(new AttributeRow(k, v != null ? v.toString() : "")));
        }
        return rows;
    }
}