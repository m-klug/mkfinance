package br.com.mkfinance.api.vault;

import br.com.mkfinance.application.vault.VaultYieldInput;
import br.com.mkfinance.application.vault.VaultYieldResult;
import org.springframework.stereotype.Component;

@Component
public class VaultYieldMapper {

    public VaultYieldInput toInput(VaultYieldRequest request) {
        return new VaultYieldInput(request.amountCents(), request.date(), request.description());
    }

    public VaultYieldResponse toResponse(VaultYieldResult result) {
        return new VaultYieldResponse(
                result.transactionId(),
                result.amountCents(),
                result.date(),
                result.shares().stream()
                        .map(share -> new VaultYieldShareResponse(share.vaultId(), share.amountCents()))
                        .toList());
    }
}
