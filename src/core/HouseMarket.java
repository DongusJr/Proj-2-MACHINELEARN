/*
 * HouseMarket: Simple post/remove market for handing a real estate market.
 * 
 * Author  : Jacky Mallett
 * Date    : May 2015
 *
 */
package core;

import java.awt.*;
import java.util.*;

import statistics.*;

import static statistics.Statistic.Type.*;

import base.Base;

// Todo - sort  inventory price order

public class HouseMarket extends Market
{
  private int maxInventory = -1; // No limit on size.
  PriorityQueue<Widget> inventory = new PriorityQueue<>(10);

 // private Statistic s_sellprice;

  // Graphics Constants

  private int totalSaleValue    = 0;
  private int totalQuantitySold = 0;


  public HouseMarket(String n, String product, Govt g, Bank b)
  {
    super(n, product, g, b, 0);

    bidPrice = -1;
    sellPrice = -1;

    useLoan = true;
    myColor = Color.blue;
    //this.inventory.lifetime = true;
    if (getDeposit() > 0)
      throw new RuntimeException(
        "Sanity failed: HouseMarket has non-zero deposit: " + getDeposit());

    s_sellprice = Statistic.getStatistic(name + ":ask-price", "prices", SINGLE);
  }

  /**
   * Constructor from gson config file. Nb. strictly, house markets don't use
   * banks, but this may change
   */

  public HouseMarket()
  {
    super();
    useLoan = true;
    //this.inventory.lifetime = true;
setProduct();
s_sellprice = Statistic.getStatistic(name + "-" + getProduct() + ":ask-price", "prices", SINGLE);
  }

  /**
   */


  /**
   * First round evaluate method for setup.
   */
  @Override
  public void evaluate()
  {
  }

  /**
   * Sell house to exchange.
   *
   * @param  house         House to sell
   * @param  askingPrice   Sale price
   * @param  account       Account of seller
   */

  public long sell(Widget house, long askingPrice, Account account)
  {
      house.price = askingPrice;
      house.owner = account.owner;

      inventory.add(house);

      bidPrice = sellPrice = inventory.peek().price;
      return askingPrice;
  }

  /**
   * Buy house listed on exchange.
   *
   * @param house   House to buy
   * @param buyer   Agent buying house
   * @param amount  Amount house is being bought for
   * 
   * @return Widget or null if sale failed.
   */
  public Widget buy(Widget house, Agent buyer, long amount)
  {
     // Verify that buyer has sufficient funds.
     if((buyer.getDeposit() < amount) || (amount < house.price))
        return null;

     // Check that house is still available for sale

     if(inventory.size() == 0)
        return null;
     else
        house = inventory.poll();

     // Available and affordable - transfer funds

System.out.println(Base.step + ": " + house.owner.getName() + " sold " + 
house.wid + " @ " + house.price + " to " + buyer.getName());
     s_sellprice.add(house.price);
     house.owner.s_income.add(house.price);
     buyer.transfer(amount, house.owner, "House sale");

     house.owner = buyer;

     // Set bid/sell prices to next one in list if available
     if(inventory.peek() != null)
     {
        bidPrice = sellPrice = inventory.peek().price;
     }
     return house;
  }

  @Override
  public Widget getLowestPrice()
  {
     return inventory.peek();
  }

  /**
   * Evaluate market for model
   *
   * @param step   step number
   * @param report t/f generated detailed report
   */
  public void evaluate(boolean report, int step)
  {
    bought = sold = false;


    if (report)
      this.print();
  }

  /**
   * Return total number of items in inventory.  Override since HouseMarket
   * redefines Inventory to use priority queue
   */
  @Override 
  public Long getTotalItems()
  {
     return (long) inventory.size();
  }
}
