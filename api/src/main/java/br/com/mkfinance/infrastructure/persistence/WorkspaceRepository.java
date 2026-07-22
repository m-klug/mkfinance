package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Workspace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
}
