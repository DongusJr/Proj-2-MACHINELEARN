/**
 * StockExchange : Act as a container for different StockMarket(s)
 *
 * Author        : Throstur Thorarensen
 * Date          : February 2015
 */


package core;

import base.Base;
import statistics.Statistic;

import java.util.*;

public class StockExchange extends Company
{

  public static StockExchange findExchange(String name, Govt govt)
  {
    for (StockExchange se : govt.getStockExchanges())
    {
      if (se.name.equals(name))
        return se;
    }
    return null;
  }

  public static StockMarket findMarket(String name, Govt govt) {
    for (StockExchange se : govt.getStockExchanges()) {
      for (StockMarket m : se.markets) {
        if (m.name.equals(name)) {
          return m;
        }
      }
    }
    return null;
  }

  public Statistic s_index;
  public boolean verbose = false;
  public Statistic s_aindex;
  public Statistic s_bindex;
  public List<StockMarket> markets = new LinkedList<>();
  public List<Agent> seats = new LinkedList<>();
  public int numSeats;

  public StockExchange()
  {
    throw new UnsupportedOperationException("No default constructor implemented for StockExchange()");
  }

  public StockExchange(String name, Govt govt, Bank bank, HashMap<String, String> properties)
  {
    super(name, 0L, govt, bank);
    String nn = properties.get("name");
    if (nn != null)
    {
      this.name = nn;
    }
    verbose = properties.get("verbose") != null;
    numSeats = properties.get("seats") != null ? Integer.parseInt(properties.get("seats")) : 10;
	System.out.println(numSeats);
    govt.registerStockExchange(this);
  }

  /*
   * 
   */
  public void initStatistics()
  {
	  super.initStatistics();

      s_index  = new Statistic(this.name + "-index", "prices", Statistic.Type.SINGLE);
      s_aindex = new Statistic(this.name + "-index-ask", "prices", Statistic.Type.SINGLE);
      s_bindex = new Statistic(this.name + "-index-bid", "prices", Statistic.Type.SINGLE);
  }

  public boolean giveSeat(Company c) {
    if (numSeats > seats.size()) {
      seats.add(c);
      return true;
    }
	System.out.println("Limit reached on Exchange Seats - " + numSeats);
    return false;
  }

  public boolean hasSeat(Agent sitter) 
  {
    return seats.contains(sitter);
  }

  public void addStockMarket(StockMarket market)
  {
    if (!markets.contains(market))
      markets.add(market);
    else throw new RuntimeException("StockMarket already on StockExchange!");
  }

  public StockMarket getRandom()
  {
    int chosen = Base.random.nextInt(markets.size());

    return markets.get(chosen);
  }

  public StockMarket getFirstOrRandom(Set<StockMarket> exclusions) {
    Collections.shuffle(markets);
    for (StockMarket m : markets) {
      if (exclusions.contains(m)) continue;
      return m;
    }
    return getRandom();
  }

  @Override
  public void evaluate(boolean report, int step) {
    long index = 0;
    long bindex = 0;
    long aindex = 0;
//    Base.DEBUG("STOCK EXCHANGE " + name);
    Collections.sort(markets, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
    for (StockMarket m : markets) {
      index += m.lastPrice;
      aindex += m.sellPrice;
      bindex += m.getBidPrice();
//      Base.DEBUG(m.name + ": [" + m.numBids() + "@" + m.bidPrice + "/" + m.numAsks() + "@" + m.sellPrice + "]");
    }
    if (markets.size() > 0) // avoid division by 0 error
    {
      index /= markets.size();
      aindex /= markets.size();
      bindex /= markets.size();
    }

    s_index.add(index);

    if (verbose) {
      s_aindex.add(aindex);
      s_bindex.add(bindex);
    }
  }

  public void releaseSeat(Agent sitter) {
    if (hasSeat(sitter)) {
      seats.remove(sitter);
    }
  }
}
