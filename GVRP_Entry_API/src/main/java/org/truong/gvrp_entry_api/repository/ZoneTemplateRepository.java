package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;

import java.util.List;

/** System templates: no branch column, read-only for branches. */
public interface ZoneTemplateRepository extends JpaRepository<ZoneTemplate, Long> {

    List<ZoneTemplate> findAllByOrderByCodeAsc();
}
