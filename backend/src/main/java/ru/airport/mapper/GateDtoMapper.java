package ru.airport.mapper;

import org.springframework.stereotype.Component;
import ru.airport.dto.GateRq;
import ru.airport.dto.GateRs;
import ru.airport.dto.GateSummaryRs;
import ru.airport.model.Gate;

/** Entity ↔ DTO для гейтов. */
@Component
public class GateDtoMapper {

    public GateRs toGateRs(Gate g) {
        if (g == null) {
            return null;
        }
        return GateRs.builder()
                .gateId(g.getGateId())
                .gateNumber(g.getGateNumber())
                .terminal(g.getTerminal())
                .isActive(g.getIsActive())
                .maxSizeCategory(g.getMaxSizeCategory())
                .build();
    }

    public GateSummaryRs toGateSummaryRs(Gate g) {
        if (g == null) {
            return null;
        }
        return GateSummaryRs.builder()
                .gateId(g.getGateId())
                .gateNumber(g.getGateNumber())
                .terminal(g.getTerminal())
                .maxSizeCategory(g.getMaxSizeCategory())
                .build();
    }

    public Gate newGate(GateRq rq) {
        return Gate.builder()
                .gateNumber(rq.getGateNumber())
                .terminal(rq.getTerminal())
                .isActive(rq.getIsActive())
                .maxSizeCategory(rq.getMaxSizeCategory())
                .build();
    }

    public void apply(GateRq rq, Gate g) {
        g.setGateNumber(rq.getGateNumber());
        g.setTerminal(rq.getTerminal());
        g.setIsActive(rq.getIsActive());
        g.setMaxSizeCategory(rq.getMaxSizeCategory());
    }
}
