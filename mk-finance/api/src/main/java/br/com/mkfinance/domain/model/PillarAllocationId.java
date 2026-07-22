package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key of {@code pillar_allocation}: (workspace_id, pillar). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PillarAllocationId implements Serializable {

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pillar", length = 20)
    private Pillar pillar;
}
