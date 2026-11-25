package com.example.smartAgr.dao.admin;

import com.example.smartAgr.model.AdminPlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminPlotDao extends JpaRepository<AdminPlot, Long> {

    List<AdminPlot> findByCurrentCrop(String currentCrop);
    List<AdminPlot> findBySoilType(String soilType);
    List<AdminPlot> findByIrrigationType(String irrigationType);
    List<AdminPlot> findByLandType(String landType);
    List<AdminPlot> findByStatus(String status);
}
