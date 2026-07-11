package org.microsoft.qintelipass.repository;

import jakarta.persistence.LockModeType;
import org.microsoft.qintelipass.models.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByIdAndCreatedBy(Long id, Long createdBy);

    Optional<Agent> findByIdAndCreatedByAndStatus(Long id, Long createdBy, Integer status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select agent from Agent agent
             where agent.id = :agentId
               and agent.createdBy = :createdBy
               and agent.status = :status
               and agent.available = true
            """)
    Optional<Agent> findActiveOwnedForUpdate(
            @Param("agentId") Long agentId,
            @Param("createdBy") Long createdBy,
            @Param("status") Integer status);

    List<Agent> findAllByCreatedByAndStatusOrderByAgentNameAsc(Long createdBy, Integer status);

    List<Agent> findAllByCreatedByAndStatusAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
            Long createdBy,
            Integer status,
            String agentName);

    List<Agent> findAllByCreatedByAndStatusAndAvailableTrueOrderByAgentNameAsc(
            Long createdBy,
            Integer status);

    List<Agent> findAllByCreatedByAndStatusAndAvailableTrueAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
            Long createdBy,
            Integer status,
            String agentName);

    Optional<Agent> findByIdAndCreatedByAndStatusAndAvailableTrue(
            Long id,
            Long createdBy,
            Integer status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Agent agent
               set agent.status = :deletedStatus
             where agent.id = :agentId
               and agent.createdBy = :createdBy
               and agent.status = :activeStatus
            """)
    int markDeleted(
            @Param("agentId") Long agentId,
            @Param("createdBy") Long createdBy,
            @Param("activeStatus") Integer activeStatus,
            @Param("deletedStatus") Integer deletedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Agent agent
               set agent.agentName = :agentName
             where agent.id = :agentId
               and agent.createdBy = :createdBy
               and agent.status = :activeStatus
            """)
    int renameActiveAgent(
            @Param("agentId") Long agentId,
            @Param("createdBy") Long createdBy,
            @Param("activeStatus") Integer activeStatus,
            @Param("agentName") String agentName);
}
