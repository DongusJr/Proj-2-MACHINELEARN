/* Program : Threadneedle
 *
 * Profile : profile is a list of the items that an agent needs or wants 
 *           every round.
 *
 *           Needs - must obtain, adverse consequences if fail - i.e. starvation
 *           Wants - will try to obtain in order of priority
 *
 * Author  : Jacky Mallett
 * Date    : February 2012
 *
 * Comments: Refactoring to use hashmap would make this more efficient.
 */
package core;

import java.util.*;
import java.util.concurrent.*;

import com.google.gson.annotations.Expose;

public class Profile
{
  @Expose
  public ConcurrentSkipListMap<String,Need> needs = new ConcurrentSkipListMap<>();
  public ConcurrentSkipListMap<String,Want> wants = new ConcurrentSkipListMap<>();


  public Profile(){}

  /**
   * Create a new profile from existing profile (deep copy)
   *
   * @param newProfile profile to use as copy
   */

  public Profile(Profile newProfile)
  {
     for(Need n : newProfile.needs.values())
         addNeed(n.product, n.quantity, n.storeQ, n.stepFrequency, 
                 n.consumption, n.consumable, n.useLoan);
  }

  @Override
  /**
   * Test if this profile matches the supplied profile
   *
   * @param obj profile to match
   * @return t/f
   */
  public boolean equals(Object obj)
  {
     if(obj == null) return false;

     if(this.getClass() != obj.getClass()) return false;

     Profile profile = (Profile)obj;

	 // Check and see if the raw number of needs is the same

	 if(this.needs.size() != profile.needs.size())
		return false;

     ConcurrentSkipListMap<String, Need> othNeeds = profile.needs.clone();

	 // Check each need, return as soon as a non-match is found
     for(Need need : this.needs.values())
     {
        if(othNeeds.get(need.product) != null)
        {
           if(!othNeeds.get(need.product).equals(need))
              return false;
        }
        else
        {
           System.out.println("Comparison failed on " + need);
           return false;
        }
     }
	 // All checks passed or will have already returned
     return true;
  }

  /**
   * Add need.
   *
   * @param name        Name of need
   * @param quantity    quantity of need to obtain each round
   * @param storeQ      size of store for need
   * @param frequency   how often to get need.
   * @param consume     how much to consume each round.
   * @param consumable  false for house, land, etc.
   * @param useLoan     take out loan to purchase if necessary
   *
   * @return  Need need created, or existing need with same name.
   */

  public Need addNeed(String name, int quantity, int storeQ, int frequency,
                      int consume, boolean consumable, boolean useLoan)
  {
    Need need = null;

    if (getNeed(name) == null)
    {
      need = new Need(name, quantity, storeQ, frequency, consume, 
                               consumable, useLoan);
      needs.put(need.product, need);
    }
    else
    {
      System.err.println("Error: Need already present " + name);
    }

    return getNeed(need.product);
  }

  /**
   * Return next need in list, or first need if at end.
   *
   * @param product - next need in list after supplied product
   *
   * @return Next need in list or first
   */

   public Need getNext(String product)
   {

      if(needs.higherEntry(product) == null)
         return needs.get(needs.firstKey());
      else
         return needs.higherEntry(product).getValue();
   }

  /**
   * Add a want to the profile. (Wants are currently defaulted
   * to consumable/no borrowing, fields are provided for forwards
   * compatability.)
   *
   * @param name      name of want to add
   * @param quantity  quantity to obtain /frequency
   * @param storeQ    quantity to store
   * @param frequency frequency with which want is examined
   * @param consume   quantity to consume each round
   * @param consumable t/f is this product consumable (can it be used up)
   * @param useLoan    take out a loan to buy this product if necessary
   */

  public void addWant(String name, int quantity, int storeQ, int frequency,
                      int consume, boolean consumable, boolean useLoan)
  {
    if (getWant(name) == null)
      wants.put(name, new Want(name, quantity, storeQ, frequency, consume));
    else
    {
      System.err.println("Error: Want already present " + name);
    }
  }

  /**
   * Get total number of needs currently provisioned.
   *
   * @return total number of needs
   */
  public int needsSize()
  {
    return needs.size();
  }

  /**
   * Get total number of wants currently provisioned.
   *
   * @return total number of wants
   */
  public int wantsSize()
  {
    return needs.size();
  }

  /**
   * Return total amount of demand for each product/round for statistics.
   *
   * @param needwants Hashmap to build totals information in.
   */

  public void addTotals(HashMap<String, Double> needwants)
  {
    for (Need need : this.needs.values())
      need.addAvgQuantity(needwants);
    for (Want want : this.wants.values())
      want.addAvgQuantity(needwants);
  }

  /**
   * Reset all values to 0.
   *
   * @return this profile
   */
  public Profile reset()
  {
    for (Need need : this.needs.values())
      need.reset();
    for (Want want : this.wants.values())
      want.reset();

    return this;
  }

  public Want getWant(String name)
  {
     return wants.get(name);
  }

  public Need getNeed(String name)
  {
     return needs.get(name);
  }

  /**
   * Returns as an integer quantity the total average demand this round of
   * both Needs and Wants.
   *
   * @return total
   */

  public int getTotalDemand()
  {
    int sum = 0;

    for (Need need : this.needs.values())
    {
      sum += need.getRequired();
    }

    for (Want want : this.wants.values())
    {
      sum += want.getRequired();
    }
    return sum;
  }

  /**
   * Return summary report of profile.
   *
   * @return summary report
   */
   public String report()
   {
      String s = "";

      for(Need need : needs.values())
          s += need.toString() + "\n";
      for(Want want : wants.values())
          s += want.toString() + "\n";

      return s;
   }
}

class Want extends Need 
{
  int priority; // relative, adjustable priority

  public Want(String p, int q, int store, int s, int c)
  {
    super(p, q, store, s, c, true, false);
  }

  @Override
  public boolean equals(Object obj)
  {
    throw new RuntimeException("Want comparison not implemented");
  }
}
