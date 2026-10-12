package com.wys.inventory.repository;

import com.wys.inventory.model.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {
    List<Action> findByActionCodeIn(List<String> actionCodes);
}
