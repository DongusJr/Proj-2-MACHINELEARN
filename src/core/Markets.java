// Program : Threadneedle
//
// Markets : Container class for individual item markets at a single location.
//           Having a separate container allows there to be multiple markets 
//           for the same items.
// 
// Author  : Jacky Mallett
// Date    : April  2012
//
// Comments:
package core;

import javafx.collections.*;

import java.util.*;

public class Markets
{
  private Govt govt;
  Bank defaultbank;                 // Default bank for new markets
  private long defaultdeposit;              // default initial deposit

  // todo: move to private?
  public LinkedList<Market>     markets    = new LinkedList<>();
  public ObservableList<Market> obsMarkets = FXCollections.observableList(markets);

  public Markets(Bank bank, Govt g, long defaultdeposit)
  {
    govt = g;
    this.defaultbank = bank;
    this.defaultdeposit = defaultdeposit;
  }

  /**
   * Create a market and add it to the list of markets managed by this
   * container.
   *
   * @param name    Name for market
   * @param product product sold by market
   * @param bank    Bank for market account
   * @param deposit Initial deposit at bank
   * @param region  Region market belongs to
   * @return Error message or null
   */

  public String createMarket(String name, String product,
                             Bank bank, long deposit, Region region)
  {
    // Check that no market already exists for this product

    if (getMarket(product) != null)
      return "A market for " + product + " already exists";

    if(bank == null)
       bank = defaultbank;

    Market market = new Market(name, product, govt, bank, deposit);

    addMarket(market);

    if(region != null)
       market.setRegion(region);
    return null;
  }


  /**
   * Create a market and add it to the list of markets managed by this
   * container. Use default values for market name, deposit, bank
   *
   * @param product product to create market for
   * @return Market
   */

  public Market createMarket(String product)
  {
    // Create the market if it doesn't already exist.
    if (getMarket(product) == null)
    {
      Market m = new Market("M-" + product, product, govt, defaultbank,
                            defaultdeposit);
      addMarket(m);
    }
    return getMarket(product);
  }

  /**
   * Remove all markets from holder. (Used to reset simulation.)
   */

  public void removeAll()
  {
    obsMarkets.removeAll();
    markets.clear();
    this.govt = null;
    this.defaultbank = null;
  }

  /**
   * Add market to this container.
   *
   * @param newMarket Maket to add.
   */

  public void addMarket(Market newMarket)
  {
    for (Market m : markets)
    {
      if (m.getProduct().equals(newMarket.getProduct()))
      {
        System.out.println("Error: Market < " + newMarket.getProduct()
                           + " > already in list");
        //throw new RuntimeException();
        return;
      }
    }
    obsMarkets.add(newMarket);
  }

  public void removeMarket(Market market)
  {
    markets.remove(market);
  }

  // TODO: replace with hash map

  /**
   * Return market for specified product. If more than one market for the same
   * product exists, the first in the list is returned. Names are case 
   * insensitive but a warning message will be printed if no match occurs, but
   * a case insensitive match would have succeeded.
   *
   * @param name Name of product
   * @return Market, or null
   */
  public Market getMarket(String name)
  {
    String caseMatch = null;

    for (Market m : markets)
    {
      if (m.getProduct().equals(name))
        return m;
      else if(m.getProduct().equalsIgnoreCase(name))
      {
         caseMatch = m.getProduct();
      }
    }

    if(caseMatch != null)
    {
      System.out.println("***Market search is case sensitive and failed to find :" + name + "***\nDid find caseInsensitive match " + caseMatch);
    }
    // System.out.println("Failed to find market in Markets: " + name);
    return null;
  }

  /**
   * Return the labour market in this collection.
   *
   * @return labour market
   */
  public LabourMarket getLabourMarket()
  {
    return ((LabourMarket) getMarket("Labour"));
  }

  /**
   * Return an iterator to allow operations on all of the markets in the
   * container.
   *
   * @return ListIterator Iterator over all markets
   */

  public Iterator<Market> getIterator()
  {
    return markets.listIterator();
  }

  /**
   * Evaluate market for this simulation step.
   *
   * @param step Step number being executed
   * @param report t/f print report on step
   */

  public void evaluate(int step, boolean report)
  {
    for (Market market : markets)
    {
      if(market instanceof LabourMarket )
         ((LabourMarket)market).evaluate(step, report);
      else
         market.evaluate(step, report);
    }
  }

  /**
   * Print out list of markets in container.
   */

  public void print()
  {
    System.out.println("#Markets =" + markets.size());
    for (Market m : markets)
      m.print();
    System.out.println("\n");
  }
}
