/**
 * Stockmarket : Handle the implementation of a stockmarket for a single share
 *
 * Author      : Throstur Thorarensen
 * Date        : December 2014 
 */
package core;

import base.Base;
import statistics.Statistic;

import java.util.*;

public class StockMarket extends Company {
  /// BEGIN INTERNAL CLASSES

  // enhanced readability with enum
  public enum OrderType
  {
    BID,
    ASK
  }

  // container for "matched" orders
  public class OrderMatch
  {
    public long  price;
    public long  volume;
    public Agent buyer;
    public Agent seller;
    public Order bid;
    public Order ask;

    public OrderMatch(long price, long volume, Agent buyer, Agent seller, Order bid, Order ask)
    {
      this.price = price;
      this.volume = volume;
      this.buyer = buyer;
      this.seller = seller;
      this.bid = bid;
      this.ask = ask;
    }
  }

  // Order to be matched with other orders, comparable implemented for TreeSet
  public class Order implements Comparable<Order>
  {
    private OrderType type;

    public long getPrice() {
      return price;
    }

    private long price;
    public long volume;
    private long timeLeft;
    private Agent agent;

    public Order(OrderType type, long price, long volume, Agent agent)
    {
      this.type = type;
      this.price = price;
      this.volume = volume;
      this.agent = agent;
      this.timeLeft = 12;
      if (volume <= 0) {
        throw new RuntimeException(agent + " attempted to place an order with " + volume + " volume.");
      }
      if (price <= 0) {
        throw new RuntimeException(agent + " attempted to place an order with invalid price: " + price);
      }
    }

    public Order(OrderType type, long price, long volume, Agent agent, long period)
    {
      this(type, price, volume, agent);
      this.timeLeft = period;
    }

    public long decreaseOrder(long amount)
    {
      if (volume - amount < 0)
      {
        throw new RuntimeException("Order decreased to below zero!");
      }

      volume -= amount;

      return volume;
    }

    /**
     * decreases timeLeft by 1
     *
     * @return the new value of timeLeft
     */
    public long elapseTime()
    {
      if (timeLeft == -1)
      {
        return -1;
      }
      return --timeLeft;
    }

    public long getTimeLeft() {
      return timeLeft;
    }

    /**
     * Tries to match this order with another order.
     *
     * @param other the other order
     * @return an OrderMatch if there is a match, null otherwise
     */
    public OrderMatch tryMatch(Order other)
    {
      // premature optimization
      if (this.type == other.type)
      {
        return null;
      }

      // can't trade zero of something
      if (this.volume == 0 || other.volume == 0)
      {
        return null;
      }

      // currently the match is implemented at "this" price, not hardcoded to buyer or seller
      if (this.type == OrderType.BID && (this.price >= other.price))
      {
        return new OrderMatch(this.price, Math.min(this.volume, other.volume), this.agent, other.agent, this, other);
      }
      else if (this.type == OrderType.ASK && (this.price <= other.price))
      {
        return new OrderMatch(this.price, Math.min(this.volume, other.volume), other.agent, this.agent, other, this);
      }

      return null;
    }

    public boolean illiquid()
    {
      if (type == OrderType.ASK)
      {
        return false;
      }

      return agent.getDeposit() < volume * price;

    }

    @Override

    public int compareTo(Order o)
    {
      int value = 0;

      if (this.price > o.price)
      {
        value = 1;
      }
      else if (this.price < o.price)
      {
        value = -1;
      }

      return value;
    }

    @Override
    public String toString() {
      return type.toString() + "@" + Long.toString(price) + "x" + Long.toString(volume) + " [" + agent.name + "] time left: " + timeLeft;
    }
  }

  /// END INTERNAL CLASSES

  private NavigableSet<Order> bids, asks;
  private StockExchange stockExchange;
  protected long bidPrice; // highest bid order (last known)
  protected long sellPrice; // lowest sell order (last known)
  protected long lastPrice; // last price traded at

  public Statistic s_sellprice;
  public Statistic s_bidprice;
  public Statistic s_tradeprice;
  public Statistic s_avgprice;

  /**
   * Constructor from gson config file.
   */
  private StockMarket()
  {
    //  inventory = new Inventory(product, false, false);
    throw new UnsupportedOperationException("Not implemented: StockMarket default constructor");
  }

  public StockMarket(String name, Govt g, Bank b)
  {
    super(name, 0L, g, b);
    Initialize();
  }

  public StockMarket(String name, Govt g, Bank b, HashMap<String, String> properties)
  {
    this(name, g, b);

    String nn = properties.get("name");
    String exchange = properties.get("exchange");
    String ipo = properties.get("ipo");

    if (nn != null)
    {
      this.name = nn;
    }

    if (exchange != null)
    {
      StockExchange stockEx = StockExchange.findExchange(exchange, govt);
      if (stockEx != null)
      {
        stockEx.addStockMarket(this);
        stockExchange = stockEx;
      }
    }

    if (ipo != null)
    {
      long volume = Long.parseLong(ipo);
      this.introduce(volume);
    }
  }

  private void introduce(long volume) {
    Shares s = new Shares(name, volume, 1L, this, this.sharesIssued);
    this.addInvestment(s);
    this.placeOrder(OrderType.ASK, 1L, volume, this, -1);
  }

  // because constructors are a bit lame :/
  private void Initialize()
  {
	//initStatistics();
    labourInput = 0;
    bidPrice    = 0;
    sellPrice   = 0;
    this.offeredSalary = 0;
    bids = Collections.synchronizedNavigableSet(new TreeSet<>());
    asks = Collections.synchronizedNavigableSet(new TreeSet<>());
  }

  public void initStatistics()
  {
	  System.out.println("Init " + name);
    s_sellprice     = new Statistic("S-" + name + ":ask-price", "stocks", Statistic.Type.SINGLE);
    s_bidprice      = new Statistic("S-" + name + ":bid-price", "stocks", Statistic.Type.SINGLE);
    s_tradeprice    = new Statistic("S-" + name + ":price", "stocks", Statistic.Type.SINGLE);
    s_avgprice      = new Statistic("S-" + name + ":avg", "stocksavg", Statistic.Type.AVERAGE);
  }


  /**
   * Places an order favorable for the market.
   *
   * @param type   Whether the order is a bid or ask
   * @param volume How many units to order
   * @param agent  The owner of the order
   * @param duration duration of order
   * @return Whether the order placement succeeded
   */
  public boolean placeOrder(OrderType type, long volume, Agent agent, 
                            long duration) 
  {
    if (volume == 0) {
      throw new IllegalArgumentException("Volume is non-positive!");
    }

    long price = 0;

    if (type == OrderType.ASK)
    {
      if (!asks.isEmpty())
      {
        price = asks.first().price - 1;
        // Don't sell for free
        if (price <= 0) price = 1;

        if (!bids.isEmpty())
        {
          if (price < bids.last().price)
          {
            price = bids.last().price;
          }
          else if (price > bids.last().price * 2)
          {
            price = Math.max((long) (bids.last().price * 1.5), asks.first().price / 3);
          }
        }
      } else {
        if (bids.isEmpty()) {
          price = Math.max(sellPrice, 1);
        } else {
          price = (long) Math.max(sellPrice, (bids.last().price * 1.5 + 1L)); // try and sell for more than buy value
        }
      }
    }
    else if (type == OrderType.BID)
    {
      if (!bids.isEmpty())
      {
        price = bids.last().price + 1; // no roof here
        // about to use `asks', so remove invalid entries.
        asks.removeIf(x -> x.timeLeft == 0 || x.volume == 0);
        if (!asks.isEmpty()) {
          if (price > asks.first().price) {
            long vol = Math.min(asks.first().volume, volume);
            long remaining = volume - vol;
            boolean val = placeOrder(type, asks.first().price, vol, agent, duration);
            if (remaining == 0)
              return val;
            return placeOrder(type, remaining, agent, duration);
          }
        }
      }
      else if (!asks.isEmpty())
      {
        price = Math.max(1, asks.first().price / 7); // to avoid setting an order we can't expect to be filled
        price = Math.max(bidPrice, price);
        price = Math.min(sellPrice, price);
        // TODO: revise the above (more)
      } else {
        price = 1; // buy for as cheap as possible!
      }
    }

    if (price > 0) {
      return placeOrder(type, price, volume, agent, 12);
    }

    return false;
  }

  /**
   * Places a specific order
   *
   * @param type   Whether the order is a bid or ask
   * @param price  The price of the order
   * @param volume How many units to order
   * @param agent  The owner of the order
   * @param duration duration for order
   * @return Whether it succeeded
   */
  public boolean placeOrder(OrderType type, long price, long volume, Agent agent, long duration) {
    if (volume <= 0) {
      throw new IllegalArgumentException("Volume is zero!");
    }

	/*
    if (!stockExchange.hasSeat(agent)) 
	{
      if (agent instanceof StockInvestor) {
        System.out.println("[NOTIFY] " + agent.name + " trading shares without seat (need arbitrator to be implemented) on " + stockExchange);
      }
      else if (!(agent instanceof StockMarket)) {
        System.err.println("[WARN] " + agent.name + " trying to buy shares on " + stockExchange + " without seat!");
      }
    }
    */
    if (type == OrderType.ASK) {
      // check if agent owns what he's selling
      Inventory inv = agent.shareholdings.get(name);

      if (inv == null)
      {
        return false;
      }
      else
      {
        Iterator<Widget> it = inv.getIterator();
        long found = 0;
        while (it.hasNext())
        {
          found += it.next().quantity();
        }
        if (found < volume)
        {
          return false;
        }
      }

    }

    Order ord = new Order(type, price, volume, agent, duration);
    if (type == OrderType.ASK) {
      asks.add(ord);
    }
    else if (type == OrderType.BID)
    {
      bids.add(ord);
    }

//    Base.DEBUG("New " + type.toString() + " order from " + agent.name + ": " + volume + "x " + name + "@" + price);

    // check if the order matches (by matching all orders)
    matchOrders();

    return true;
  }

  /**
   * Atomic transaction of shares on the stock market
   *
   * @param name     Name of the shares that are being traded
   * @param quantity Number of shares being traded
   * @param price    Price per share
   * @param from     Seller
   * @param to       Purchaser
   * @return success t/f
   */
  public boolean transferShares(String name, long quantity, long price, Agent from, Agent to)
  {
    // check if there is enough money first
    if (to.getDeposit() < price * quantity)
    {
      return false; // value?
    }

    // check if the shares are owned first
    long shareholding = from.getShareholding(name);
    if (shareholding < quantity)
    {
      return false; // transfer partial? nah probably not
    }

    // only pay for the sold amount (should be equal to quantity)
    long sold = from.transferShares(name, quantity, to);

    assert (sold == quantity);

    if (!to.transfer(price * sold, from, "Shares: '" + name + "'x" + sold + "@" + price))
    {
      to.transferShares(name, sold, from); // couldn't afford them
      return false;
    }

    // record the sale (for each traded share)
    for (int i = 0 ; i < quantity; ++i)
    {
      s_avgprice.add(price);
    }

    return true;
  }

  public void orderTransaction(OrderMatch om)
  {
    if (om.volume == 0) {
      throw new RuntimeException("0 sized order can't be transacted!");
    }

    // check if the orders still exist
    if (om.ask == null || om.bid == null)
    {
      Base.DEBUG("Transaction with expired order failed.");
      return;
    }

    if (om.seller == null || om.buyer == null)
    {
      Base.DEBUG("Seller or buyer is null, break!");
      throw new RuntimeException("What the hell happened here! (buyer or seller null in orderTransaction)"); // probably dead code
    }

    if (transferShares(name, om.volume, om.price, om.seller, om.buyer))
    {
      om.bid.decreaseOrder(om.volume);
      om.ask.decreaseOrder(om.volume);

      // record the purchase
      if (om.buyer instanceof StockInvestor) {
        ((StockInvestor) om.buyer).recordPurchase(om.price, om.volume);
      }
      if (om.seller instanceof StockInvestor) {
        ((StockInvestor) om.seller).recordSale(om.price, om.volume);
      }
      lastPrice = om.price;
    }
    else
    {
      if (om.buyer.getDeposit() < om.price * om.volume)
      {
        // buyer is broke
        cancelOrder(om.bid);
      }
      if (om.seller.getShareholding(name) < om.volume)
      {
        cancelOrder(om.ask);
      }
    }
  }


  public void evaluate()
  {
    throw new UnsupportedOperationException("base evaluate is not done!");
  }

  protected void evaluate(boolean report, int step) 
  {
    // make time pass for the orders, and remove if expired or empty
    bids.removeIf(x -> x.elapseTime() == 0 || x.volume == 0);
    asks.removeIf(x -> x.elapseTime() == 0 || x.volume == 0);

    // naively try to match orders
    matchOrders();

    s_sellprice.add(Math.max(Math.max(0, getAskPrice()), sellPrice));
    s_bidprice.add(getBidPrice());
    s_tradeprice.add(lastPrice);

	printOrders();
  }

  private void matchOrders() {
    // TODO: Remove from evaluate or from here... (code duplication)
    bids.removeIf(x -> x.timeLeft == 0 || x.volume == 0);
    asks.removeIf(x -> x.timeLeft == 0 || x.volume == 0);
    // remove orders that can't be filled
    bids.removeIf(Order::illiquid);

    // remove invalid orders (like sell orders to non-existent shares)
    bids.removeIf(x -> x.volume > x.agent.getShareholding(this.name) && x.type == OrderType.ASK);

    // avoid concurrent modification exception by taking a copy
    NavigableSet<Order> cBids = new TreeSet<>();
    NavigableSet<Order> cAsks = new TreeSet<>();
    cBids.addAll(bids);
    cAsks.addAll(asks);

    Iterator<Order> itBid = cBids.descendingIterator();
    Iterator<Order> itAsk = cAsks.iterator();

    while (itBid.hasNext() && itAsk.hasNext()) {
      Order bid = itBid.next();
      Order ask = itAsk.next();

      OrderMatch om = bid.tryMatch(ask);
      if (om != null) {
        orderTransaction(om);
      } else {
        break;
      }
    }
  }

  public long numBids()
  {
    return bids.size();
  }

  public long numAsks()
  {
    return asks.size();
  }

  public TreeSet<Order> ordersBy(Agent owner, OrderType type)
  {
    TreeSet<Order> orders = new TreeSet<>();

    if (type == null || type == OrderType.ASK) {
      orders.addAll(asks);
    }
    if (type == null || type == OrderType.BID) {
      orders.addAll(bids);
    }

    orders.removeIf(x -> x.agent != owner);

    return orders;
  }

  public boolean cancelOrder(Order o)
  {
    if (o.type == OrderType.BID)
    {
      return bids.remove(o);
    }
    else
    {
      return asks.remove(o);
    }
    // TODO note: possibly can be refactored into ``return (bids.remove(o) || asks.remove(o));''
  }
  // TODO? buy/sell methods? other methods? Average price etc

  public long getBidPrice() {
    if (bids.isEmpty()) {
      return 0L;
    }
    bidPrice = bids.last().price;

    return bidPrice;
  }

  public long getAskPrice()
  {
    if (asks.isEmpty())
    {
      return -1L;
    }
    sellPrice = asks.first().price;

    return sellPrice;
  }


  public void printOrders(Agent ag) {
    for (Order o : ordersBy(ag, null)) {
      System.out.println(this.name + " " + o.toString());
    }
  }

  public void printOrders() {
    System.out.println("--------[ASKS]---------");
    Iterator<Order> it = asks.descendingIterator();
    while (it.hasNext())
    {
      System.out.println(it.next().toString());
    }
    it = bids.descendingIterator();
    System.out.println("--------[BIDS]---------");
    while (it.hasNext())
    {
      System.out.println(it.next().toString());
    }
  }

  /**
   * A stock market doesn't own a deposit account like most agents.
   *
   * @return 0
   */
  @Override
  public long getDeposit()
  {
    return 0;
  }

}
