package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.WorkspaceInvite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, String> {
}
