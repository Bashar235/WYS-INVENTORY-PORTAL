package com.wys.inventory.service;

import com.wys.inventory.model.Category;
import com.wys.inventory.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service("categoryService")
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    public List<Category> findAll() {
        List<Category> all = categoryRepository.findAll();
        all.sort(Comparator.comparing(c -> c.getName().toLowerCase()));
        return all;
    }

    @Override
    public Category create(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be blank.");
        }
        String trimmed = titleCase(name.trim());
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("Category '" + trimmed + "' already exists.");
        }
        return categoryRepository.save(new Category(trimmed));
    }

    @Override
    public boolean existsByName(String name) {
        return name != null && categoryRepository.existsByNameIgnoreCase(name.trim());
    }

    /** Simple title-case: capitalises the first letter of each word. */
    private String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
