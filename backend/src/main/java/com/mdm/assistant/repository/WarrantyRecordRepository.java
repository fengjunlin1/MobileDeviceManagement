package com.mdm.assistant.repository;

import com.mdm.assistant.entity.WarrantyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WarrantyRecordRepository extends JpaRepository<WarrantyRecordEntity, Long> {
    Optional<WarrantyRecordEntity> findByDeviceId(String deviceId);
    Optional<WarrantyRecordEntity> findBySnCode(String snCode);
    Optional<WarrantyRecordEntity> findByUserIdAndSnCode(Long userId, String snCode);

    @Query("SELECT w FROM WarrantyRecordEntity w WHERE w.warrantyEnd BETWEEN :start AND :end")
    List<WarrantyRecordEntity> findWarrantiesExpiringBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT w FROM WarrantyRecordEntity w WHERE w.userId = :userId AND w.warrantyEnd BETWEEN :start AND :end")
    List<WarrantyRecordEntity> findWarrantiesExpiringBetweenByUserId(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}