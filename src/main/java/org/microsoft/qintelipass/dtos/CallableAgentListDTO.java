package org.microsoft.qintelipass.dtos;

import java.util.List;

public record CallableAgentListDTO(List<CallableAgentDTO> items, int total) {
}
