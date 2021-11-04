package core;

/**
 * Program : Threadneedle
 * Author  : Throstur Thorarensen
 * Date    : 22.3.2015.
 */
public abstract class AbstractInvestmentStrategy implements InvestmentStrategy 
{

  long deposit;
  long upcomingExpenses;
  private Class<? extends InvestorStrategy> investorStrategy = DefaultInvestorStrategy.class;

  AbstractInvestmentStrategy() {
    this.deposit = 0;
  }

  // code duplication, revise
  public abstract InvestmentStrategyGoal getGoalFromStrategy();

  @Override
  public void updateStrategy(long deposit, long nextExpenses /* ... */ ) {
    this.deposit = deposit;
    this.upcomingExpenses = nextExpenses;
  }

  @Override
  public InvestorStrategy getInvestorStrategy(StockInvestor i) {
    try {
      return investorStrategy.getConstructor(StockInvestor.class).newInstance(i);
    }
    catch (Exception e) {
      throw new RuntimeException("Couldn't find strategy: " + investorStrategy.getSimpleName());
    }
  }

  @Override
  public void setInvestorStrategy(Class<? extends AbstractInvestorStrategy> cls) {
    try {
      cls.getConstructor(StockInvestor.class);
      investorStrategy = cls;
    }
    catch (NoSuchMethodException e) {
      System.err.println("Couldn't change investor strategy: " + e.getMessage());
    }
  }
}

