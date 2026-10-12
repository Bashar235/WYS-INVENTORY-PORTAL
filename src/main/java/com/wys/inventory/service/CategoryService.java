package com.wys.inventory.service;

import com.wys.inventory.model.Category;

import java.util.List;

public interface CategoryService {

    /** Returns all categories sorted alphabetically by name. */
    List<Category> findAll();

    /**
     * Creates and persists a new category.
     *
     * @param name the category name (trimmed, title-cased)
     * @return the saved {@link Category}
     * @throws IllegalArgumentException if a category with that name already exists
     */
    Category create(String name);

    /** Returns true if a category with the given name exists (case-insensitive). */
    boolean existsByName(String name);
}
