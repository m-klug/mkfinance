package br.com.mkfinance.domain.service.fixedincome;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;

/**
 * What one application is worth, net, the day it is redeemed: the gross amount less IOF, income
 * tax and B3 custody, each decided by how long the money was held. Shared by the simulator (one
 * settlement per contribution) and by a position (one settlement for the whole title).
 *
 * <p>Money is rounded to the cent at each step and the taxes are rounded in cents too, so the parts
 * always add up to the totals shown. Known simplifications: custody is 0.20% a.a. on the gross
 * balance, pro rata by calendar days, with no exemption band.
 */
public final class FixedIncomeSettlement {

    private static final double CUSTODY_ANNUAL = 0.002;

    /** IOF on the yield, in percent, by days held 1..29; zero from day 30 on. */
    private static final int[] IOF_PERCENT_BY_DAY = {
        96, 93, 90, 86, 83, 80, 76, 73, 70, 66, 63, 60, 56, 53, 50,
        46, 43, 40, 36, 33, 30, 26, 23, 20, 16, 13, 10, 6, 3
    };

    private FixedIncomeSettlement() {
    }

    public record Result(long grossCents, long iofCents, long incomeTaxCents, long custodyCents) {

        public long netCents() {
            return grossCents - iofCents - incomeTaxCents - custodyCents;
        }
    }

    /**
     * @param principalCents what was applied
     * @param growth growth factor over the whole time held
     * @param ageDays calendar days between the application and the redemption
     */
    public static Result settle(InstrumentType type, long principalCents, double growth, long ageDays) {
        long gross = Math.round(principalCents * growth);
        long yield = gross - principalCents;

        long iof = 0;
        long incomeTax = 0;
        if (type.taxed() && yield > 0) {
            iof = Math.round(yield * iofRate(ageDays));
            incomeTax = Math.round((yield - iof) * incomeTaxRate(ageDays));
        }
        long custody = 0;
        if (type.hasCustody()) {
            custody = Math.round(gross * CUSTODY_ANNUAL * ageDays / 365.0);
        }
        return new Result(gross, iof, incomeTax, custody);
    }

    /** Share of the yield taken by IOF: only below 30 days, falling from 96% to 3%. */
    static double iofRate(long ageDays) {
        if (ageDays < 1 || ageDays > IOF_PERCENT_BY_DAY.length) {
            return 0;
        }
        return IOF_PERCENT_BY_DAY[(int) ageDays - 1] / 100.0;
    }

    /** Regressive income-tax table by days held. */
    static double incomeTaxRate(long ageDays) {
        if (ageDays <= 180) {
            return 0.225;
        }
        if (ageDays <= 360) {
            return 0.20;
        }
        if (ageDays <= 720) {
            return 0.175;
        }
        return 0.15;
    }
}
