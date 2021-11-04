package core;

import base.Base;
import core.StockMarket.Order;

import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Program : Threadneedle
 * Author  : Throstur Thorarensen
 * Date    : 22.3.2015.
 */
public class RandomInvestorStrategy extends AbstractInvestorStrategy {

  public RandomInvestorStrategy(StockInvestor investor) {
    super(investor);
  }

  @Override
  public void executeStrategy(InvestmentStrategyGoal goal) {
    switch (goal) {
      case CONTRACT:
      case LIQUIDATE:
        TradeConservatively();
        break;
      default:
        TradeRandomly();
        break;
    }
  }

  private void TradeConservatively() {
    int choice = Base.random.nextInt(3);
    switch (choice) {
      case 0:
      case 1: // sell
        investor.sellShares();
        break;
      case 2: // liquidate
        investor.liquidate();
        break;
      default:
        // intentionally left blank
        break;
    }
  }

  private void TradeRandomly() {
    int choice = Base.random.nextInt(8);
    switch (choice) {
      case 0:
      case 2:
        if (Base.random.nextBoolean()) {
          investor.buyShares(Base.random.nextInt(10) + 1, 0, Base.random.nextInt(33) + 3);
        }
      case 1:
      case 3:
        if (Base.random.nextBoolean()) {
          investor.sellShares();
        }
      case 4:
        if (Base.random.nextBoolean()) {
          TreeSet<Order> orders = investor.getOrders(null);
          TreeSet<Order> removals = orders.stream().filter(o -> Base.random.nextBoolean()).collect(Collectors.toCollection(TreeSet::new));
          investor.cancelOrders(removals);
        }
      case 5:
        if (Base.random.nextBoolean()) {
          profitFromPurchases();
        }
      case 6:
        protectAgainstLosses();
      default:
        // intentionally left blank
        break;
    }
  }
}
