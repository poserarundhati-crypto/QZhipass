package org.microsoft.qintelipass.models;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.util.Snowflake;

import java.time.OffsetDateTime;

@Setter
@Getter
@ToString
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", updatable = false, nullable = false, unique = true)
    @JsonSerialize(using = ToStringSerializer.class)
    @Builder.Default
    private Long id = Snowflake.nextId();
    @Column(name = "phone", nullable = false, unique = true)
    private String phone;
    @Column(name = "email", unique = true)
    private String email;
    @Column(name = "password_hash")
    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.NORMAL;
    @Column(name = "username", nullable = false, unique = true)
    private String name;
    @Column(name = "department")
    private String department;
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = Snowflake.nextId();
        }
        if (status == null) {
            status = UserStatus.NORMAL;
        }
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
    }
}
