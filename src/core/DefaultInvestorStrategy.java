package core;

/**
 * Program : Threadneedle
 * Author  : Throstur Thorarensen
 * Date    : 23.3.2015.
 */
public class DefaultInvestorStrategy extends AbstractInvestorStrategy {

  public DefaultInvestorStrategy(StockInvestor investor) {
    super(investor);
  }

  @Override
  public void executeStrategy(InvestmentStrategyGoal goal) {
    switch(goal) {
      case LIQUIDATE:
        investor.liquidate();
        break;
      case CONTRACT:
        investor.sellShares();
        break;
      case EXPAND:
        investor.buyShares();
        break;
      case NONE:
      default:
        investor.buyShares();
        profitFromPurchases();
        break;
    }
    protectAgainstLosses();
  }

}
