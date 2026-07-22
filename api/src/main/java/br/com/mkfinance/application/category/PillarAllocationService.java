package br.com.mkfinance.application.category;

import br.com.mkfinance.domain.model.PillarAllocation;
import br.com.mkfinance.infrastructure.persistence.PillarAllocationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PillarAllocationService {

    private final PillarAllocationRepository pillarAllocationRepository;

    public List<PillarAllocation> list(UUID workspaceId) {
        return pillarAllocationRepository.findByWorkspaceId(workspaceId);
    }
}
