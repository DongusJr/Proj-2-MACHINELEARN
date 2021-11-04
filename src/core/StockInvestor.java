/* Program  : Threadneedle
 *
 * Investor : Investors are agents who trade shares. They receive
 *            dividends from a specified bank, and invest in a single
 *            company's shares. Isolating behaviour down to a single 
 *            agent/share pair makes analysis more tractable.
 * 
 * Author   : Throstur
 * Date     : October 2012
 * Comments : Investor's have 0 salary and receive all income from 
 *            dividends/interest
 * Todo:    : make investment Company throughout
 */
package core;

import com.google.gson.annotations.Expose;
import core.StockMarket.Order;
import core.StockMarket.OrderType;

import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.*;

import static base.Base.*;

public class StockInvestor extends Person
{
  @Expose
  StockMarket stockMarket = null; // Target for investors share trades

  int minInvestAmount = 50;

  // investmentCompany not really in use atm

  String investmentCompany = null; // Company to invest in.
  InvestorStrategy strategy = new DefaultInvestorStrategy(this);

  @Override
  // Main loop for agent.
  public void evaluate(boolean report, int step) 
  {
    super.evaluate(report, step);
   
    payDebt();

    if (stockMarket != null) 
	{
	  // Get available funds
      long deposit = getDeposit();

	  // If funds available
      if ((deposit > minInvestAmount)) 
	  {
		// Place order for shares
        if (stockMarket.placeOrder(OrderType.BID, 1, this, 3)) 
		{
          System.out.println(name + " ordered investment (" + stockMarket.getName() + ")");
        }
      }

	  // Example of selling. Be sure to have sufficient funds to pay debt for each round.
      if (deposit < 5) // "if poor, sell at least one shares"
      {
        if (getShareholding(stockMarket.name) > 0) 
		{
          if (stockMarket.placeOrder(OrderType.ASK, 1, this, 3)) 
		  {
            System.out.println(name + " trying to sell investment (" + stockMarket.getName() + ")");
          }
        }
      }
	  // Example of taking out a loan - this can be done at any time.
	  else
	  {
		  requestLoan(100, 1);
      }
	}
  }

  /**
   * Constructor within model.
   *
   * @param name       Unique and identifying name
   * @param g          government
   * @param b          bank where account is
   * @param properties Property map interface with fxml:
   *                       deposit Initial deposit with bank
   */

  public StockInvestor(String name, Govt g, Bank b, HashMap<String, String> properties)
  {
    super(name, g,b,Integer.parseInt(properties.get("initialDeposit")));

    this.employer = this;		// Stock Investors are self employed
    this.investmentCompany = properties.get("trading"); // get share to trade in.

    setSalary(0L);              // StockInvestors will work for free for some reason

  }

  public StockInvestor()
  {
    super();
  }

  /**
   * Allow StockInvestor's to have a salary of 0, and ignore minimum wage
   *
   * @param newSalary New value for salary
   */
  @Override
  public void setSalary(long newSalary)
  {
    salary = newSalary;
    desiredSalary = newSalary;
  }

  private boolean requestLoan(long amount, int duration) 
  {
//    Base.DEBUG("SEEKING FUNDING: " + name + " (" + amount + ", dur: " + duration + "). Current: " + getAccount().getDeposit());
    if (getAccount().debts.size() == 0) {
      if (getBank().zombie) {
        System.out.println("[WARNING] :: Bank is defunct!");
        System.err.println("Warning. Bank is zombie.");
      }
      try
      {
        return getBank().requestLoan(this.getAccount(), amount, duration, Time.MONTH,
                BaselWeighting.MORTGAGE, Loan.Type.COMPOUND) != null;
      }
      catch (RuntimeException e)
      {
        System.err.println(e.getMessage());
        e.printStackTrace();
        return false; // DIRTY DIRTY HACK!
      }
    }
    return false;
  }


  /**
   * @param c Company investor will invest in.
   */

  public void setInvestment(Company c) 
  {
    investmentCompany = c.name;

    if (c instanceof StockMarket)
    {
      stockMarket = (StockMarket) c;
    }
  }

  public void buyShares() {
    buyShares(1, 0, 12);
  }

  public void buyShares(long volume, long price, long duration) {
    if (stockMarket != null) {
      if (price != 0) {
        stockMarket.placeOrder(OrderType.BID, price, volume, employer, duration);
      } else {
        stockMarket.placeOrder(OrderType.BID, volume, employer, duration);
      }
    }
  }

  /**
   * Try to sell at buy price
   */
  public void liquidate() {
    if (employer instanceof InvestmentCompany) {
      long volume = employer.getShareholding(stockMarket.getName());
      if (volume == 0) {
        return; // can't sell nothing :(
      }
      sellShares(volume, stockMarket.bidPrice, 1);
    }
  }

  public void sellShares() {
    TreeSet<Order> orders = getOrders(OrderType.ASK);
    if (orders.size() == 0) {
      sellShares(1, 0, 12);
      return;
    }
    long volume = 1; // sell 1 more
    long duration = 3; // 3 is minimum
    long maxPrice = Long.MAX_VALUE; // find the lowest price, which becomes the upper limit on our ask
    for (Order o : orders) {
      volume += o.volume;
      long tl = o.getTimeLeft();
      if (duration < tl) {
        duration = tl;
      }
      long p = o.getPrice();
      if (p < maxPrice) {
        maxPrice = p;
      }
      stockMarket.cancelOrder(o);
    }
    long price = maxPrice > stockMarket.getAskPrice() ? stockMarket.sellPrice : maxPrice;

    sellShares(volume, price, duration);
  }

  public void sellShares(long volume) {
    sellShares(volume, 0, 12);
  }

  public void sellShares(long volume, long price, long duration) {
    if (volume <= 0) return; // don't allow selling zero shares (rounding point artifact)
    if (stockMarket != null) {
      if (price != 0) {
        stockMarket.placeOrder(OrderType.ASK, price, volume, employer, duration);
      } else {
        stockMarket.placeOrder(OrderType.ASK, volume, employer, duration);
      }
    }
    else
    {
      throw new RuntimeException("Stock market is null for shares!");
    }
  }

  public TreeSet<Order> getOrders(OrderType type) {
    return stockMarket.ordersBy(employer, type);
  }

  /**
   * Todo: Sell investment (allow investors to trade)
   *
   * @param to Agent to sell to
   * @param amount amount to sell for
   * @param period for loans only - duration when issued
   * @param type of investment
   * @return Investment being sold
   */

  public Object sellInvestment(Agent to, int amount, int period, String type)
  {
    throw new RuntimeException("Not implemented for this institution "
                               + this.name);
  }

  /**
   * Cancel orders
   *
   * @param orders to cancel
   */
  public void cancelOrders(TreeSet<Order> orders)
  {
    if (orders == null)
    { // cancel all orders
      // cancel all asks
      getOrders(OrderType.ASK).forEach(stockMarket::cancelOrder);

      // cancel all bids
      getOrders(OrderType.BID).forEach(stockMarket::cancelOrder);

    } 
	else {
      getOrders(OrderType.ASK).stream().filter(orders::contains).forEach(stockMarket::cancelOrder);
      getOrders(OrderType.BID).stream().filter(orders::contains).forEach(stockMarket::cancelOrder);
    }

  }

  public InvestorStrategy getStrategy() {
    return strategy;
  }

  public void setStrategy(InvestorStrategy is) {
    strategy = is;
  }

  public void workWithStrategy(InvestmentStrategyGoal goal) {
    strategy.executeStrategy(goal);
  }

  public void allowSellAt(long price, long low, long high) {
    if (price <= stockMarket.bidPrice) {
      sellShares(employer.getShareholding(stockMarket.name));
    }
    else
    {
      long totalVolume = employer.getShareholding(stockMarket.name);
      sellShares(totalVolume / 2, price, 3);
      sellShares(totalVolume / 3, high, 6);
    }

    TreeSet<Order> cancellations = new TreeSet<>();

    // cancel any orders that are worse
    cancellations.addAll(getOrders(OrderType.ASK).stream().filter(o -> o.getPrice() < price).collect(Collectors.toList()));
    cancellations.addAll(getOrders(OrderType.BID).stream().filter(o -> o.getPrice() > price).collect(Collectors.toList()));
    cancelOrders(cancellations);

    // finally, try to buy again for the lowest price we have bought at
    if (low < price) {
      buyShares(1, low, 3);
    }
  }

  public void sellIfBelow(long price) {
    // A.K.A. panic sell
    if (stockMarket.sellPrice < price) {
      sellShares(employer.getShareholding(stockMarket.name));
    }
  }

  public void recordSale(long price, long volume) {
    // TODO: Refactor maybe (profile first)
    for (int i = 0; i < volume; i++) {
      strategy.recordSale(price);
    }
  }
  public void recordPurchase(long price, long volume) {
    // TODO: Refactor maybe (profile first)
    for (int i = 0; i < volume; i++) {
      strategy.recordPurchase(price);
    }
  }
}
