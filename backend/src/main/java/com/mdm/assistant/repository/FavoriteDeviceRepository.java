package com.mdm.assistant.repository;

import com.mdm.assistant.entity.FavoriteDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteDeviceRepository extends JpaRepository<FavoriteDeviceEntity, Long> {
    List<FavoriteDeviceEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<FavoriteDeviceEntity> findByUserIdAndDeviceCategoryOrderByCreatedAtDesc(String userId, String deviceCategory);
    Optional<FavoriteDeviceEntity> findByUserIdAndDeviceName(String userId, String deviceName);
    boolean existsByUserIdAndDeviceName(String userId, String deviceName);
    @Modifying
    @Transactional
    void deleteByUserIdAndDeviceName(String userId, String deviceName);
    long countByUserId(String userId);
    @Modifying
    @Transactional
    void deleteByUserId(String userId);

    @Query("SELECT f FROM FavoriteDeviceEntity f WHERE f.userId = :userId AND " +
           "REPLACE(LOWER(f.deviceName), ' ', '') LIKE %:pattern%")
    List<FavoriteDeviceEntity> findByUserIdAndDeviceNameIgnoreCaseAndNoSpace(
            @Param("userId") String userId, 
            @Param("pattern") String pattern);

    @Query("SELECT f FROM FavoriteDeviceEntity f WHERE f.userId = :userId AND " +
           "REPLACE(LOWER(f.deviceName), ' ', '') = REPLACE(LOWER(:deviceName), ' ', '')")
    Optional<FavoriteDeviceEntity> findByUserIdAndDeviceNameIgnoreCaseAndNoSpaceExact(
            @Param("userId") String userId, 
            @Param("deviceName") String deviceName);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM FavoriteDeviceEntity f WHERE f.userId = :userId AND " +
           "REPLACE(LOWER(f.deviceName), ' ', '') = REPLACE(LOWER(:deviceName), ' ', '')")
    boolean existsByUserIdAndDeviceNameIgnoreCaseAndNoSpace(
            @Param("userId") String userId, 
            @Param("deviceName") String deviceName);

    @Modifying
    @Transactional
    @Query("DELETE FROM FavoriteDeviceEntity f WHERE f.userId = :userId AND " +
           "REPLACE(LOWER(f.deviceName), ' ', '') = REPLACE(LOWER(:deviceName), ' ', '')")
    void deleteByUserIdAndDeviceNameIgnoreCaseAndNoSpace(
            @Param("userId") String userId, 
            @Param("deviceName") String deviceName);
}
