package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Program : Threadneedle
 * Author  : Throstur Thorarensen
 * Date    : 22.3.2015.
 */
public abstract class AbstractInvestorStrategy implements InvestorStrategy {

  protected StockInvestor investor;
  protected List<Long> purchases;
  protected PurchaseStats pStats;
  protected double profitMargin = 1.05;

  protected AbstractInvestorStrategy(StockInvestor investor) {
    this.investor = investor;
    if (investor.getStrategy() != this) {
      investor.setStrategy(this);
    }
    purchases = new ArrayList<>();
  }

  public final void executeStrategy() {
    executeStrategy(InvestmentStrategyGoal.NONE);
  }

  @Override
  public void recordPurchase(long value) {
    purchases.add(value);
  }

  @Override
  public void recordSale(long value) {
    if (purchases.isEmpty()) {
      throw new RuntimeException("Selling something without recording previous purchase!?");
    }
    Collections.sort(purchases);
    purchases.remove(0);
  }

  public abstract void executeStrategy(InvestmentStrategyGoal goal);

  protected void profitFromPurchases() {
    long average, high, low;

    pStats = new PurchaseStats().invoke();
    average = pStats.getTotal();
    low = pStats.getLow();
    high = pStats.getHigh();
    if (purchases.isEmpty())
      return;
    average /= purchases.size();

    long price = (long) (average * profitMargin);

    investor.allowSellAt(price, low, high);
  }

  protected void protectAgainstLosses() {
    if (purchases.isEmpty()) return;
    if (pStats == null) {
      pStats = new PurchaseStats().invoke();
    }
    long price = (long) ((1 / profitMargin) * pStats.getTotal() / purchases.size());
    investor.sellIfBelow(price);
  }

  protected class PurchaseStats {
    private long total;
    private long high;
    private long low;

    public PurchaseStats() {
      this.total = 0;
      this.high = Long.MIN_VALUE;
      this.low = Long.MAX_VALUE;
    }

    public PurchaseStats(long average, long high, long low) {
      this.total = average;
      this.high = high;
      this.low = low;
    }

    public long getTotal() {
      return total;
    }

    public long getHigh() {
      return high;
    }

    public long getLow() {
      return low;
    }

    public PurchaseStats invoke() {
      for (Long price : purchases) {
        total += price;
        high = Math.max(high, price);
        low  = Math.min(low,  price);
      }
      return this;
    }
  }
}
