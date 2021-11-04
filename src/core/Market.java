/*
 * Market  : Handle the implementation of a market for a single good 
 * 
 * Author  : Jacky Mallett
 * Date    : February 2012
 *
 * Comments: Interface methods to the market are named from the user 
 *           perspective, i.e market.buy() will buy from the market, 
 *           which from the market's point of view is a sale.
 *          
 * Todo:     lifetime handling. Allow markets to sell oldest goods first
 * 
 *           totalquantitysold gets reset outside of the market's control
 *           so using it as part of the price limitation process is a little
 *           iffy
 */
package core;

import com.google.gson.annotations.Expose;
import statistics.Statistic;

import java.util.*;

import static base.Base.*;
import static base.Base.random;
import static statistics.Statistic.Type.SINGLE;

public class Market extends Company
{
  @Expose public int maxEmployees = 1;          // Max. no. of employees
  @Expose public boolean bidEqualsAsk = false;  // Allow bid price to equal ask
  @Expose public int  maxInventory  = 20;       // Maximum amount in inventory.
  @Expose public int  ttl = -1;                 // Time to Live
  @Expose public int minSpread = 1;             // Mininum spread
  @Expose public int maxSpread = 5;             // Maximum spread
  @Expose public boolean payDividend = false;   // pay dividend
  
  public boolean useLoan = false;       // t/f purchase requires a loan.

  
  public    Inventory inventory;        // Items Market owns
  protected long      bidPrice;         // Price at which market will buy at
  protected long      sellPrice;        // Price at which market will sell at
  protected long      spread;           // Spread to maintain between Bid/Ask
  protected boolean bought = false;     // Did market clear this round.
  protected boolean sold   = false;     // Did market clear this round.

  protected int salesTax   = 0; // %age sales tax added to prices
  protected int cashBuffer = 4; // Fraction of deposit held as buffer

  // Round counters that get picked up for statistics

  private int totalSaleValue     = 0;
  private int totalPurchaseValue = 0;
  private int totalQuantitySold  = 0;

  // Statistics

  public int s_soldLastRound = 0;
  public int s_soldThisRound = 0;
  public int s_maxInventory  = 0; // No. of times market was full

  public Statistic s_inventory;
  public Statistic s_sellprice;
  public Statistic s_bidprice;
  
  /**
   * Constructor from fxml file.
   *
   * @param name       Unique and identifying name for market
   * @param govt       Government market belongs to
   * @param bank       Bank used by market.
   * @param properties Properties from fxml file.
   */

  public Market(String name, Govt govt, Bank bank,
                HashMap<String, String> properties)
  {
    this(name, properties.get("product"), govt, bank, 
         Long.parseLong(properties.get("initialDeposit")));
  }

  /**
   * Constructor
   *
   * @param name           Unique and identifying name for market
   * @param product        Unique identifier for market's product
   * @param initialDeposit Initial bank deposit
   * @param govt           Government market belongs to
   * @param bank           Bank used by market.
   */

  public Market(String name, String product, Govt govt, Bank bank,
                long initialDeposit)
  {
    super(name, initialDeposit, govt, bank);
    System.out.println(name + " " + getAccount());
    this.product = product.trim();

    // Use setProduct since this also initialises the statistic counters.

    inventory = new Inventory(null, true, false);
    setProduct();

    /*
     * Set initial buy sell price. 
     */

    bidPrice = 4;
    sellPrice = 5;
    this.offeredSalary = 1;

  }

  /**
   * Constructor from gson config file.
   */

  public Market()
  {
    // At this point, product won't be set correctly, so has
    // to be fixed later.
    inventory = new Inventory(product, true, false);

    bidPrice = 4;
    sellPrice = 5;
    this.offeredSalary = 1;

    // Todo: do we need to super() to Agent here??
    if(!regionName.equals(""))
       this.region = govt.regions.get(regionName);
  }

  /**
   * Provide common initialisation for statistics, and initialisation of
   * product name for inventory. Futures: extend for multi-markets (extends
   * statistic naming convention)
   */

  public void setProduct()
  {
    if (inventory.product == null)
    {
      inventory.product = this.product;

      s_inventory = Statistic.getStatistic(getProduct(), "inventory", SINGLE);

      s_sellprice = Statistic.getStatistic(getProduct() + ":ask-price", "prices", SINGLE);
      s_bidprice =  Statistic.getStatistic(getProduct() + ":bid-price", "prices", SINGLE);

    }
    else
      System.out.println("Product is already set in " + this.name);

	spread = minSpread;
  }

  /**
   * Change ttl to new value. This also impacts items in existing
   * inventory. (If their ttl was left unchanged, there would be 
   * the potential for some immortal items hanging around in an 
   * otherwise expiring inventory, which would be confusing.
   *
   * @param new ttl
   */
  public void setTTL(int ttl)
  {
     this.ttl = ttl;
	 inventory.setTTL(ttl);
  }

  /**
   * Reset model - return new instance based on config.
   *
   * @param govt     Government object for this instance
   * @param bank     Bank for market
   * @param markets  Market container
   * @return New instance based on config parameters
   */

  public Market reset(Govt govt, Bank bank, Markets markets)
  {
    System.out.println("Re-implement reset");
    return null;
  }

  /**
   * Return product managed by this market
   *
   * @return name of product
   */
  public String getProduct()
  {
    return inventory.product;
  }

  /**
   * Return current bid price for market
   *
   * @return bid price
   */
  public long getBidPrice()
  {
    return bidPrice;
  }

  /**
   * Return current ask price for market
   *
   * @return ask price
   */
  public long getAskPrice()
  {
    return sellPrice;
  }

  /**
   * Return the maximum # of items market can buy.
   *
   * @return Max # of items
   */
  public long getMaxLot()
  {
    long maxItems;

    // Check that the market hasn't reached its' maximum inventory.

    if (inventory.getTotalItems() >= this.maxInventory)
	{
	  adjustPrices(-1);
      return 0;
	}
	else if( this.maxInventory == 0)
	  maxItems = getDeposit()/bidPrice;
    else
      maxItems = this.maxInventory - inventory.getTotalItems();

    // Test against the amount of cash available to buy at current
    // price.
    return Math.min(getDeposit()/ bidPrice, maxItems);
  }

  /**
   * Return accumulated total sales value and reset
   *
   * @return accumulated total sale value since last reset
   */

  public long resetTotalSaleValue()
  {
    long r = totalSaleValue + totalPurchaseValue;

    totalPurchaseValue = 0;
    totalSaleValue     = 0;

    return r;
  }

  /**
   * Return accumulated total quantity sold and reset
   *
   * @return accumulated total sale value since last reset
   */

  public long resetTotalQuantitySold()
  {
    long r = totalQuantitySold;
    totalQuantitySold = 0;

    return r;
  }

  /**
   * Sell goods to the market maker. If more goods are offered than
   * the market maker can buy, none are bought.
   *
   * @param  w            container of items to be sold
   * @param  askingPrice  Asking price (-1 for market price)
   * @param  account      Account to pay purchase price to
   * @return price sold at (0 unable to buy, -1 could buy at lower price)
   */

  public long sell(Widget w, long askingPrice, Account account)
  {
    long price;

    /*
     * Sell to the market maker at the current price. Only buy up to 
     * the limit of the inventory size.
     */
    if (inventory.getTotalItems() >= this.maxInventory)
    {
      System.out .println(name + " unable to buy - inventory limit reached :" + this.maxInventory );
      s_maxInventory++;
      // -- jmadjustPrices(-1);
      return 0;
    }

    // Take the current market price

    if (askingPrice == -1)
      price = bidPrice;
    else
      price = askingPrice;

    // Check that sale quantity 
    if (w.quantity() <= getMaxLot())
    {
      if (!getAccount().transfer(account, price * w.quantity(),
                                 "sale to market: " + product))
      {
        System.err.println("Sell failed - insufficient funds "
                           + (price * w.quantity()));
        return -1;
      }
      else
      {
        inventory.add(w);
        bought = true;
        totalPurchaseValue += w.quantity() * price;
        return price;
      }
    }
    else
    /*
     * Market is unable to buy at this price due to insufficient funds.
     */
    {
      System.err.println("*** " + name + " Sell failed - insufficient funds " 
                          + price * w.quantity());
      adjustPrices(-1);
    }
    return 0;
  }

  /**
   * Buy goods from the market maker at the market price
   *
   * @param quantity Quantity to buy
   * @param buyer    Buyer's account to transfer cost from
   * @return Inventory Inventory containing items bought
   */

  public Inventory buy(long quantity, Account buyer)
  {
    return buy(-1, quantity, buyer);
  }

  // Provided here for compatibility with other markets.
  public Widget buy(Widget w, Agent buyer, long amount)
  {
     throw new RuntimeException("Not supported for class Market");
  }

  /**
   * Buy goods from the marketMaker
   *
   * @param  askingPrice Maximum price willing to buy at
   * @param  quantity    Quantity to buy
   * @param  buyer       Buyer's account to transfer cost from
   * @return Inventory   Inventory containing items bought
   */
  public Inventory buy(long askingPrice, long quantity, Account buyer)
  {
    Inventory newinv = new Inventory(inventory.product, inventory.lifetime,
                                     inventory.unique);
    long purchaseSize;
    long price;
    long salesTaxLevied;

    purchaseSize = quantity;
    if (inventory.getTotalItems() == 0)
    {
      return null;
    }

    if (inventory.getTotalItems() < quantity)
      purchaseSize = inventory.getTotalItems();

   /*
    * Is this purchase at market price (-1) - or has the buyer specified a
    * price? Sales tax is added if applicable and then paid directly to the
    * government
    * 
    * FIX: buyers shouldn't be able to arbitrarily specify a price to buy
    * at...
    */

    if (askingPrice == -1)
    {
      salesTaxLevied = (sellPrice * salesTax) / 100;
      price = sellPrice + salesTaxLevied;
    }
    else
    {
      System.out.println("Warning: buyer specified price");
      salesTaxLevied = (askingPrice * salesTax) / 100;
      price = askingPrice + salesTaxLevied;
    }

	/*
     * Check funds are available and buy if they are.
	 */

    if (buyer.getDeposit() > price * purchaseSize)
    {
      if (!buyer.transfer(getAccount(), price * purchaseSize,
                          "purchase from market: " + product))
      {
        // Money transfer failed, so no purchase.
        return null;
      }
      else
      {
        totalSaleValue += price * purchaseSize;
        totalQuantitySold += purchaseSize;
        s_soldThisRound += purchaseSize;

        s_income.add(price * purchaseSize);

        // Pay any sales tax to the Government

        if (salesTax > 0)
        {
          transfer(salesTax * purchaseSize, govt, "Sales tax: "
                   + salesTax + " on " + purchaseSize + " "
                   + getProduct());
        }

        DEBUG("Market:" + inventory.product + " sold #" + purchaseSize
              + " @ $" + sellPrice + " to " + buyer.getName() + " ["
              + getAccount().getDeposit() + "]");

        // inventory.audit();
        inventory.remove(purchaseSize, newinv);
        // inventory.audit();

        sold = true;

        Iterator<Widget> itr = newinv.getIterator();

        // Set price items were sold for

        while (itr.hasNext())
        {
          Widget w = itr.next();
          w.lastSoldPrice = price;
        }

        return newinv;
      }
    }

    return null;
  }

  /**
   * Adjust prices based on inventory behaviour. Ceiling on purchase price is
   * the amount of money that the market has.
   * <p>
   * Ref: Garmen Market MicroStructure.
   */
  private void adjustPrices()
  {
    // Inventory is growing
    if(inventory.getTotalItems() > s_inventory.get(2))
    {
      if (bidPrice > 1)
      {
        DEBUG("Inventory is growing - reducing prices: "
              + inventory.getTotalItems() + " " + s_inventory.get(2)
              + " Bid: " + bidPrice + " Sell:" + sellPrice);

        adjustPrices(-1);
      }
    }
    // Inventory is shrinking
    else if (inventory.getTotalItems() < s_inventory.get()) 
    {
      // Limit bid price by available funds and size of inventory.
      // to prevent price being raised above that the market can 
      // afford to buy at.
      long maxItems = maxInventory - inventory.getTotalItems();

      // Nasty things happen at the limit, since the market can create
      // a situation where it buys 1 item at max price.
 
      if(maxItems < 3) maxItems = 3;

      if(getDeposit() < bidPrice * maxItems)
      {
        DEBUG("Blocking price increase on funding issues: ");
        DEBUG("     Available:  " + getDeposit());
        DEBUG("     Needed   :  " + (maxInventory - inventory.getTotalItems()) + " * " + bidPrice);
      }
      else
      {
        DEBUG("Inventory is shrinking - increasing prices");
        adjustPrices(1);
      }
    }
    // 0 is an edge case since inventory can be 0 for multiple rounds (if
    // selling all inventory each round), but there is also the issue
	// of not selling at all.
    else if(inventory.getTotalItems() == 0)
    {
       adjustPrices(1);
    }
	// There are several edge cases where the inventory isn't changing,
	// because prices are too high, and only 1 item is being bought each
	// round. This isn't entirely incorrect if there's a monoply buyer
	// for example, but we will restrict this a little here.
	else 
	{
	   adjustPrices(-1);
	}

    s_sellprice.add(sellPrice);
    s_bidprice.add(bidPrice);
    //DEBUG(name + " Price: " + bidPrice + " " + sellPrice);
  }

  /**
   * Adjust prices by specified amount. 
   *
   * @param amount Amount to adjust prices.
   * @return t/f   Adjustment may fail if it takes the bidprice below 0 or above
   *               the maxmimum amount of money available to buy goods.
   */
  private boolean adjustPrices(long amount)
  {
	/*
	 * Check that the adjustment results in a price >= 1
	 */

    if (bidPrice + amount < 1)
        return false;

    long maxItems      = this.maxInventory - inventory.getTotalItems();

	if(maxItems < 3) maxItems = 3;

    long availableCash = getDeposit() - (maxItems * bidPrice);

	// Don't increase prices, if new price is too high for market
	// to be able to restock at. 
    if((availableCash < getDeposit()/cashBuffer) && amount >= 1)
       return false;
    else
    {
        bidPrice  += amount;
        sellPrice += amount;
    }

    return true;
  }

  /**
   * Increase spread between bid and sell price. Increase is performed
   * on the sellPrice - i.e. bid price remains the same.
   *
   * @param amount Value to increase spread by.
   */
  private void increaseSpread(int amount)
  {
	if(spread + amount < maxSpread)
    {
		spread += amount;
        sellPrice = bidPrice + spread;
	}
	else 
		spread = maxSpread;

  }

  /**
   * Decrease spread between bid and sell price. Decrease is performed
   * on the sellPrice - i.e. bid price remains the same.
   *
   * @param amount Value to increase spread by.
   */
  private void decreaseSpread(int amount)
  {
	if(spread - amount > minSpread)
		spread -= amount;
	else
	   spread = minSpread;

	sellPrice = bidPrice + spread;
  }

  /**
   * Return buy/sell difference
   *
   * @return spread
   */
  public long getSpread()
  {
    return sellPrice - bidPrice;
  }

  // todo:Need to debug price changes.

  // First round
  public void evaluate()
  {
  }

  protected void evaluate(boolean report, int step)
  {
    if ((employees.size() < maxEmployees))
    {
        hireEmployee();
    }

    Collections.shuffle(employees, random);

    // Pay employees and hire/fire. Maintain a sufficient
    // cash reserve so that can always buy at least 1
    // item of stock at current bidPrice following 
    // salary payment.

    Iterator<Person> iter = employees.iterator();

    while (iter.hasNext())
    {
      Person p = iter.next();

      // Retaining 2 * bidPrice allows a margin if prices change.
      // Alternatively the market could always maintain a cushion
      // that is the minimum bidPrice and allow the price adjustment
      // to find that level. 
      //
      // todo: many economic mechanisms borrow in this situation and
      //       this needs to be available as an alternative/control

      if (getAccount().getDeposit() - p.getSalary() > 2 * bidPrice)
      {
        p.paySalary(getAccount());
        DEBUG(name + " Paid salary to " + p.name + "/" + p.getSalary());
      }
      else
      {
        DEBUG(step + " Market: " + inventory.product
              + " Unable to pay salary:" + p.name + " ["
              + getAccount().getDeposit() + "/" + p.getSalary() + "]");

        fireEmployee(p, iter);
        if (offeredSalary > govt.minWage)
          offeredSalary -= 1;
      }
    }

    adjustPrices();

	if(getAccount().incoming - getAccount().outgoing < 0)
		increaseSpread(1);

  // payTax(govt.corporateTaxRate, govt.corporateCutoff);
  govt.payCorporateTax(this.getAccount(), s_income.get());

	// If payDividend is enabled then treat as partnership and distribute 
	// amongst current employees
	if(payDividend && ((getAccount().getDeposit() > minCapital * 1.1) &&
	   (employees.size() > 0)))
	{
       long dividend = (getAccount().getDeposit()-minCapital)/employees.size(); 

	   for(Person p : employees)
	   {
           getAccount().transfer(p.getAccount(), dividend, "Market Dividend (" +
				   this.name + ")");
	   }

	}

    if (report)
      this.print();

    s_inventory.add(inventory.getTotalItems());

    s_soldLastRound = s_soldThisRound;
    s_soldThisRound = 0;

    bought = sold = false;

	if(ttl > 0)
       inventory.expire();
  }

  /**
   * Return widget with current lowest price.
   *
   * @return Widget with current lowest price
   * todo: this doesn't extend well currently for base class
   */
  public Widget getLowestPrice()
  {
     if(inventory.size() == 0)
        return null;
     else if (inventory.size() == 1)
        return inventory.getFirst();
     else
     {
        Collections.sort(inventory.inventory, new Comparator<Widget>(){
           @Override
           public int compare(Widget lhs, Widget rhs)
           {
              return (int)(lhs.price - rhs.price);
           }
      });
     }
     return inventory.getFirst();
  }

  public Long getTotalItems()
  {
    return inventory.getTotalItems();
  }

  /**
   * Return name of Market
   */

  public String getName()
  {
    return name;
  }

  public String getCurrentSetup()
  { 
	 return name + " " + product + " " + getBankName() + ": " + getDeposit();
  }

  public void print()
  {
    System.out.println("Market " + name + ": " + inventory.product);
    for(Person p : employees)
        System.out.println(p.Id + " " + p.getSalary());
  }

}
