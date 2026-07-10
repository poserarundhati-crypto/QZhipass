package org.microsoft.qintelipass.dtos;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageRankDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userName;
    private Long totalTokens;
    private Integer rank;
}
