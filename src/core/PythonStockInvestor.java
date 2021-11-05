/* Program  : Threadneedle
 *
 * Author   : Stephan Schiffel
 * Date     : October 2021
 */
package core;

import com.google.gson.annotations.Expose;
import core.StockMarket.Order;
import core.StockMarket.OrderType;

import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.*;

import static base.Base.*;

import py4j.ClientServer;
import py4j.GatewayServer;


public class PythonStockInvestor extends StockInvestor
{
  
  public static class State {
      public long deposit; // current bank deposit
      public long debt; // current debt
      public long shareHolding; // nb of shares held currently
      public long bidPrice, askPrice; // current bid and ask price of the stock on the market
      public boolean isZombie;
      // TODO: extend this as needed
      
      public State(long deposit, long debt, long shareHolding, long bidPrice, long askPrice, boolean isZombie) {
        this.deposit = deposit;
        this.debt = debt;
        this.shareHolding = shareHolding;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.isZombie = isZombie;
      }
  }

  public static interface IPythonStockInvestor {
      public String getNextAction(int step, String agentId, State s);
  }

  @Override
  // Main loop for agent.
  public void evaluate(boolean report, int step) 
  {
    payDebt();
    System.out.println(name + ": evaluate step " + step + ", employer: " + employer.name);
    
    State s;
    if (stockMarket != null) {
      s = new State(getDeposit(), getDebt(), getShareholding(stockMarket.name), stockMarket.getBidPrice(), stockMarket.getAskPrice(), getBank().zombie);
      System.out.println(s.deposit);
      String action = pythonStockInvestor.getNextAction(step, this.name, s);
      System.out.println(name + ": action =" + action);
      if (action.equals("sellShares")) {
        sellShares();
      } else if (action.equals("liquidate")) {
        liquidate();
      } else if (action.equals("buyShares")) {
        buyShares();
      } else if (action.equals("requestLoan")) {
        requestLoan(100,1);
      } else if (action.equals("wait")) {
      } else {
        System.out.println(name + ": unknown action");
      }
    } else {
      System.out.println(name + ": has no stockmarket yet");
    }
    if (getBank().zombie) {
      System.exit(0);
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

  public PythonStockInvestor(String name, Govt g, Bank b, HashMap<String, String> properties)
  {
    super(name, g,b,properties);
    employer = this; // we are self employed
    initPythonConnection();
    String stockMarketName = properties.get("stockmarket");
    if (stockMarketName == null) {
      System.out.println(name + ": ERROR: need to set stockmarket property");
    } else { 
      System.out.println(name + ": setting stockmarket to " + stockMarketName);
      setInvestment(StockExchange.findMarket(stockMarketName, g));
    }
  }

  public PythonStockInvestor()
  {
    super();
    initPythonConnection();
  }

  private static IPythonStockInvestor pythonStockInvestor;

  private static void initPythonConnection() {
    if (pythonStockInvestor == null) {
      ClientServer clientServer = new ClientServer(null);
      // We get an entry point from the Python side
      pythonStockInvestor = (IPythonStockInvestor) clientServer.getPythonServerEntryPoint(new Class[] { IPythonStockInvestor.class });
    }
  }
  
  @Override
  public void setUnemployed() {
    // do nothing, so we don't get hired by anyone
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

}
