// Program : Threadneedle
//
// Need    : Need is used by profile to represent items an agent must try to
//           obtain every round.
// 
// Author  : Jacky Mallett
// Date    : February 2012
//
// Comments:
package core;

import java.util.*;

import com.google.gson.annotations.Expose;

/**
 * Needs (and wants) are referenced through the agent's profile. At this time it
 * is assumed that each agent has only one need, and no wants. GUI i/f and
 * simulation handling will need factoring when this changes.
 */
public class Need 
{
  @Expose public String product;        // Item required
  @Expose public int    quantity;       // Max to buy each round.
  @Expose public int    storeQ;         // Max store size
  @Expose public int    consumption;    // Amount consumed each step
  @Expose public int    stepFrequency;  // # of steps for each 'need' (currently unused)
  @Expose public boolean useLoan;       // Purchase requires a loan
  @Expose public boolean consumable;    // Houses, etc. are nonConsumable.

  public long lastPricePaid;            // Last price paid for item
  public Inventory store;               // Current holdings of item

  public Need(String p, int q, int storeQ, int stepF, int consumption, 
              boolean consumable, boolean useLoan)
  {
    this.product = p;
    this.quantity = q;
    this.storeQ = storeQ;
    this.stepFrequency = stepF;
    this.consumption = consumption;
    this.consumable  = consumable;
    this.useLoan     = useLoan;

    this.store = new Inventory(this.product, true, false);
  }

  /**
   * Initialisation for when loading from json file.
   */
  public void init()
  {
    if (this.store == null)
      this.store = new Inventory(this.product, true, false);
  }

  /**
   * Provide test for equality of contents. Note this is part of an equality
   * test chain originating from Profile comparison.
   *
   * @param    obj  Need to test against
   * @return   t/f
   * 
   */
  @Override
  public boolean equals(Object obj)
  {
     if(obj == null) return false;

     if(this.getClass() != obj.getClass()) return false;

     Need need = (Need)obj;

     return need.product.equals(this.product)
            &&  (need.storeQ        == this.storeQ)
            &&  (need.quantity      == this.quantity)
            &&  (need.consumption   == this.consumption)
            &&  (need.consumable    == this.consumable)
            &&  (need.useLoan       == this.useLoan)
            &&  (need.stepFrequency == this.stepFrequency);
  }

  /**
   * Return a string representation of the numeric parameters.
   *
   * @return quantity | store | consumption
   */
  @Override
  public String toString()
  {
    return product + " : " + quantity + " | " + storeQ + " | " + consumption;
  }

  /**
   * Initialise dynamic data.
   */
  public void reset()
  {
    lastPricePaid = 0;
    this.store = new Inventory(this.product, true, false);
  }

  /**
   * Return quantity required this round.
   *
   * @return quantity required this round
   */

  public long getRequired()
  {
    long q;

    q = this.storeQ - this.store.getTotalItems();

    // Check how much we are allowed to buy this round.

    if (q > quantity)
      return quantity;
    else
      return q;
  }

  /**
   * Remove the number of items from inventory that are consumed each round.
   *
   * @return Amount consumed, or (-) number of items that were short.
   */

  public long consume()
  {
    long consume = this.consumption;
    long shortage = 0;
    if (consume > this.store.getTotalItems())
      shortage = -1 * (consume - this.store.getTotalItems());
    else
      shortage = consume;

    if(consumable)
    {
      while ((consume > 0) && (this.store.getTotalItems() > 0))
      {
        this.store.consume(1);
        consume--;
      }
    }

    updateTTL();

    return  shortage;
  }

  /**
   * Update the time to live counter on all inventory items, and remove
   * any expired.
   */

  private void updateTTL()
  {
     Widget w;

     for(int i = 0; i < store.inventory.size(); i++)
     {
        w = store.inventory.get(i);
        // if ttl == -1 item is immortal
        if(w.ttl > 0) w.ttl--;

        if(w.ttl == 0)
        {
           store.remove(w); 
        }
     }
  }

  /**
   * Return the average quantity demanded each round. Fractional quantities
   * will be returned if quantity/frequency specify that.
   *
   * @return average quantity demanded each round.
   */
  public Double getAvgQuantity()
  {
    return ((double) (this.quantity / this.stepFrequency));
  }

  /**
   * Add average quantity to supplied hashmap for stats.
   *
   * @param totals Hashmap being used to collate stats.
   */
  public void addAvgQuantity(HashMap<String, Double> totals)
  {
    if (totals.containsKey(this.product))
    {
      totals.put(this.product,
                 totals.get(this.product) + this.getAvgQuantity());
    }
    else
    {
      totals.put(this.product, this.getAvgQuantity());
    }
  }

}
