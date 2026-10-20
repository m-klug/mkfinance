package br.com.mkfinance.domain.service.fixedincome;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeResult.Point;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects a fixed-income investment to its redemption, gross and net of IOF, income tax and B3
 * custody.
 *
 * <p>Each contribution is its own application: it accrues over the business days between its date
 * and the redemption (252 a year), and is taxed by its own age, so a long-held initial amount and a
 * recent monthly contribution fall in different income-tax brackets. The CDI, Selic and IPCA are
 * held constant over the term (the caller supplies them); there is no forward curve.
 *
 * <p>Money is in cents and the projection is done in {@code double}: relative error stays near
 * 1e-15, orders of magnitude below a cent for any amount a person invests. Every contribution is
 * rounded to the cent before its taxes are taken, and the taxes are rounded in cents too, so the
 * parts always add up to the totals shown.
 *
 * <p>Known simplifications: custody is 0.20% a.a. on the gross balance, pro rata by calendar days,
 * with no exemption band; IOF and income tax use the contribution's age in calendar days at
 * redemption. Pure and static: no Spring, no clock.
 */
public final class FixedIncomeSimulator {

    private FixedIncomeSimulator() {
    }

    public static FixedIncomeResult simulate(FixedIncomeParams params) {
        Breakdown end = redeemOn(params, params.endDate());

        List<Point> series = new ArrayList<>(params.months() + 1);
        for (int month = 0; month <= params.months(); month++) {
            LocalDate date = params.startDate().plusMonths(month);
            Breakdown atDate = redeemOn(params, date);
            series.add(new Point(month, date, atDate.invested, atDate.gross, atDate.net()));
        }

        long netYield = end.net() - end.invested;
        double netReturn = end.invested == 0 ? 0 : (double) netYield / end.invested * 100;
        return new FixedIncomeResult(
                end.invested,
                end.gross,
                end.gross - end.invested,
                end.iof,
                end.incomeTax,
                end.custody,
                end.net(),
                netYield,
                netReturn,
                netAnnualEquivalent(params, end.net()),
                List.copyOf(series));
    }

    /** Everything that was applied up to {@code redemption}, valued and taxed on that day. */
    private static Breakdown redeemOn(FixedIncomeParams params, LocalDate redemption) {
        GrowthModel growth = new ConstantRateGrowth(params);
        Breakdown total = new Breakdown();
        for (Application application : applications(params, redemption)) {
            long age = ChronoUnit.DAYS.between(application.date, redemption);
            FixedIncomeSettlement.Result settled = FixedIncomeSettlement.settle(
                    params.type(), application.cents, growth.factor(application.date, redemption), age);

            total.invested += application.cents;
            total.gross += settled.grossCents();
            total.iof += settled.iofCents();
            total.incomeTax += settled.incomeTaxCents();
            total.custody += settled.custodyCents();
        }
        return total;
    }

    /** The initial amount and each monthly contribution made on or before {@code redemption}. */
    private static List<Application> applications(FixedIncomeParams params, LocalDate redemption) {
        List<Application> applications = new ArrayList<>();
        if (params.initialCents() > 0) {
            applications.add(new Application(params.startDate(), params.initialCents()));
        }
        if (params.monthlyContributionCents() > 0) {
            for (int month = 1; month < params.months(); month++) {
                LocalDate date = params.startDate().plusMonths(month);
                if (date.isAfter(redemption)) {
                    break;
                }
                applications.add(new Application(date, params.monthlyContributionCents()));
            }
        }
        return applications;
    }

    /**
     * The annual rate that makes each application, compounded for its own calendar time, add up to
     * the net amount. Solved by bisection; the left side grows with the rate.
     */
    private static double netAnnualEquivalent(FixedIncomeParams params, long netCents) {
        LocalDate end = params.endDate();
        List<Application> applications = applications(params, end);
        double low = -0.99;
        double high = 10;
        for (int i = 0; i < 200; i++) {
            double mid = (low + high) / 2;
            double value = 0;
            for (Application application : applications) {
                double years = ChronoUnit.DAYS.between(application.date, end) / 365.0;
                value += application.cents * Math.pow(1 + mid, years);
            }
            if (value < netCents) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2 * 100;
    }

    private record Application(LocalDate date, long cents) {
    }

    private static final class Breakdown {
        long invested;
        long gross;
        long iof;
        long incomeTax;
        long custody;

        long net() {
            return gross - iof - incomeTax - custody;
        }
    }
}
