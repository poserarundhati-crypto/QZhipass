package org.microsoft.qintelipass.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agents")
public class Agent {
    public static final int STATUS_DELETED = 0;
    public static final int STATUS_ACTIVE = 1;

    @Id
    @Column(name = "agent_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "agent_name", nullable = false, length = 128)
    private String agentName;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "status", nullable = false)
    private Integer status;
}
