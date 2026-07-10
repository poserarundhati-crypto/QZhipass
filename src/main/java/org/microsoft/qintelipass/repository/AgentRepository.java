package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByIdAndCreatedBy(Long id, Long createdBy);

    Optional<Agent> findByIdAndCreatedByAndStatus(Long id, Long createdBy, Integer status);

    List<Agent> findAllByCreatedByAndStatusOrderByAgentNameAsc(Long createdBy, Integer status);

    List<Agent> findAllByCreatedByAndStatusAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
            Long createdBy,
            Integer status,
            String agentName);

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
