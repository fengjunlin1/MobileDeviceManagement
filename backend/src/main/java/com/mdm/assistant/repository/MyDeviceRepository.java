package com.mdm.assistant.repository;

import com.mdm.assistant.entity.MyDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MyDeviceRepository extends JpaRepository<MyDeviceEntity, Long> {
    List<MyDeviceEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<MyDeviceEntity> findByUserIdAndDeviceNameContainingIgnoreCaseOrderByCreatedAtDesc(Long userId, String keyword);
    
    @org.springframework.data.jpa.repository.Query("SELECT d FROM MyDeviceEntity d WHERE d.userId = :userId AND REPLACE(d.deviceName, ' ', '') LIKE CONCAT('%', REPLACE(:keyword, ' ', ''), '%') ORDER BY d.createdAt DESC")
    List<MyDeviceEntity> findByUserIdAndDeviceNameIgnoringSpaces(Long userId, String keyword);
    List<MyDeviceEntity> findByUserIdAndDeviceNameOrderByCreatedAtDesc(Long userId, String deviceName);
    Optional<MyDeviceEntity> findFirstByUserIdAndDeviceNameOrderByCreatedAtDesc(Long userId, String deviceName);
    Optional<MyDeviceEntity> findByUserIdAndSnCode(Long userId, String snCode);
    List<MyDeviceEntity> findByUserIdAndBrandContainingIgnoreCaseOrderByCreatedAtDesc(Long userId, String brand);
    Optional<MyDeviceEntity> findByUserIdAndId(Long userId, Long id);
    boolean existsByUserIdAndSnCode(Long userId, String snCode);
    long countByUserId(Long userId);

    @Modifying
    @Transactional
    void deleteByUserIdAndId(Long userId, Long id);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}