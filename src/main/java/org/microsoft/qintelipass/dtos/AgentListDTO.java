package org.microsoft.qintelipass.dtos;

import java.util.List;

public record AgentListDTO(List<AgentSummaryDTO> items, int total) {
}
