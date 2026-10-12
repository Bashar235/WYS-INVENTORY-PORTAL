package com.wys.inventory.repository;

import com.wys.inventory.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    /** Check if a category with the given name already exists (case-insensitive). */
    boolean existsByNameIgnoreCase(String name);

    /** Find a category by exact name. */
    Optional<Category> findByName(String name);
}
