package org.killbill.billing.plugin.amberflo.catalog.client;

import lombok.Getter;
import org.killbill.billing.catalog.api.BillingPeriod;

public enum BillingPeriodEnum {
  DAILY("day", "-Daily", BillingPeriod.DAILY),
  WEEKLY("week", "-Weekly", BillingPeriod.WEEKLY),
  MONTHLY("month", "-Monthly", BillingPeriod.MONTHLY),
  YEARLY("year", "-Yearly", BillingPeriod.ANNUAL),
  NO_BILLING_PERIOD("", "", BillingPeriod.NO_BILLING_PERIOD);

  @Getter private String amberfloPeriod;

  @Getter private String suffix;

  @Getter private BillingPeriod billingPeriod;

  BillingPeriodEnum(String input, String suffix, BillingPeriod billingPeriod) {
    this.amberfloPeriod = input;
    this.suffix = suffix;
    this.billingPeriod = billingPeriod;
  }

  public static BillingPeriodEnum parseBillingPeriod(String amberfloPeriod) {
    for (BillingPeriodEnum period : BillingPeriodEnum.values()) {
      if (period.getAmberfloPeriod().equals(amberfloPeriod)) {
        return period;
      }
    }

    return BillingPeriodEnum.NO_BILLING_PERIOD;
  }
}
