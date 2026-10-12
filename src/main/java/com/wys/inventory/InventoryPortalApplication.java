package com.wys.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WYS Inventory Portal — Spring Boot entry point.
 *
 * All seed data (roles, menus, actions, users, products) is handled by
 * Flyway migrations in src/main/resources/db/migration/:
 *   V1__init_schema.sql — full PostgreSQL schema
 *   V2__seed_data.sql   — seed roles, menus, actions, users, sample products
 *
 * No CommandLineRunner seeder is needed here.
 */
@SpringBootApplication
public class InventoryPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryPortalApplication.class, args);
    }
}