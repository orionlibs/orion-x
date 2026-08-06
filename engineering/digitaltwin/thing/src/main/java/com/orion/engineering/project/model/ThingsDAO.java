package com.orion.engineering.project.model;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThingsDAO extends JpaRepository<ThingModel, UUID> {

}