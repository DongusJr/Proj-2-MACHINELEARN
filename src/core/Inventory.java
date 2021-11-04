/* 
 * Program   : Threadneedle
 *
 * Inventory : Provide a contained class for handling Widgets
 * 
 * Author    : Jacky Mallett
 * Date      : April 2012
 *
 * Todo      : refactor gettotalitems handling to be more efficient
 *
 * Comments  : It's not possible in a generic class to create instances of
 *             E - hence the re-shuffling.
 *
 */
package core;

import java.util.*;

public class Inventory
{
  public  LinkedList<Widget> inventory         = new LinkedList<>();
  private LinkedList<Long>   totalItemsHistory = new LinkedList<>();

  private int checkCount  = 0; // counter for running runtime sanity checks
  private int growthBound = 1; // No of history steps for inventory behaviour

  public String  product;
  public boolean lifetime;     // Does objective have a TTL?
  public boolean unique;       // Is object unique and unmergable i.e. human

  /**
   * Constructor.
   *
   * @param name     Name of item in inventory
   * @param lifetime Whether item has a finite time to live
   * @param unique   Is item unique and unmergeable - i.e human
   */

  public Inventory(String name, boolean lifetime, boolean unique)
  {
    this.product = name;
    this.lifetime = lifetime;
    this.unique = unique;
  }

  /**
   * Constructor for general purpose inventory, which is not tied to a
   * specific item name.
   */

  public Inventory()
  {
    lifetime = true; // Must assume mixed inventory types
    unique = true; // ditto
  }

  /**
   * Print out list of inventory contents.
   */
  public void audit()
  {
    System.out.println("===  Audit " + product + " size: "
                       + getTotalItems() + "===");
    for (Widget w : inventory)
    {
      System.out.println(w + " " + w.getClass().getName() + " "
                         + w.quantity());
    }
    System.out.println("");

  }

  /**
   * Adjust total number of items in inventory. Stack is held at 10 items, and
   * history is updated.
   */
  private void adjustTotalItems()
  {
    totalItemsHistory.addFirst(getTotalItems());

    // Limit stack length to 10
    if (totalItemsHistory.size() > 10)
    {
      totalItemsHistory.removeLast();
    }
  }

  /**
   * Return total number of items in the inventory, as opposed to the total
   * number of widgets containing them. (Clumsy way to do it, but less
   * debugging than trying to manage separately.)
   *
   * @return Total number of items in the inventory.
   */

  public long getTotalItems()
  {
    long total = 0;

    for (Widget e : inventory)
    {
      total += e.quantity();
    }

    // System.out.println(product + ": get total items called " + total);

    return total;
  }

  /**
   * Return total number of containers in the inventory - nb. containers may
   * hold more than one item.
   *
   * @return Total number of containers.
   */

  public long size()
  {
    return inventory.size();
  }

  public boolean growing()
  {
    System.out.println("\n" + product + ": Growing - history: "
                       + getTotalItems());
    for (int i = 0; i < totalItemsHistory.size(); i++)
      System.out.print(totalItemsHistory.get(i) + " ");

    System.out.println("getFirst(): " + totalItemsHistory.getFirst()
                       + " > " + totalItemsHistory.get(1));

    // Check bounds
    if (totalItemsHistory.size() <= 3)
      return false;
    System.out.println("-------- ");

    return totalItemsHistory.getFirst() > totalItemsHistory.get(1);
  }

  public boolean shrinking()
  {
    System.out.println(product + ": shrinking: " + getTotalItems());
    for (int i = 0; i < totalItemsHistory.size(); i++)
      System.out.print(totalItemsHistory.get(i) + " ");

    System.out.println("getFirst(): " + totalItemsHistory.getFirst()
                       + " > " + totalItemsHistory.get(1));

    // Check bounds
    if (totalItemsHistory.size() <= 3)
      return false;

    System.out.println("-------- ");

    // Force the issue if there isn't any inventory
    return totalItemsHistory.size() == 0 || totalItemsHistory.getFirst() < totalItemsHistory.get(1);

  }

  /**
   * Add to inventory.
   *
   * @param w Object to be added
   */

  public void add(Widget w)
  {
    if (!w.name.equals(product))
    {
      throw new RuntimeException("Invalid Widget type in add " + w.name
                                 + " != " + this.product);
    }

	/*
     * Check that Widget has the correct ttl characteristics.
	 */
    if (!lifetime && w.ttl != -1)
    {
      throw new RuntimeException("TTL for Widget in non-ttl inventory");
    }

	/*
     * Indestructible objects are lumped together into one Widget container,
	 * destructible ones are held separately as TTL may be different.
	 */
    if (unique || lifetime || inventory.size() == 0)
    {
      inventory.add(w);
    }
    else
    {
      inventory.getFirst().merge(w);

      if (inventory.getFirst().lastSoldWhen < w.lastSoldWhen)
             inventory.getFirst().lastSoldWhen = w.lastSoldWhen;
    }
    adjustTotalItems();
  }

  /*
   * Sort
   * 
   * Sort inventory based on comparator provided by the object container for
   * this inventory.
   */

  public void sort()
  {
    Collections.sort(inventory, Widget::compareTo);
  }

	/*
   * Merge two inventories
	 * 
	 * @param Newinv inventory whose contents will be added to this one.
	 */

  public void merge(Inventory toMergeInv)
  {
    if (!this.product.equals(toMergeInv.product))
    {
      throw new RuntimeException("Invalid Widget type in add "
                                 + toMergeInv.product + " != " + product);
    }

    inventory.addAll(toMergeInv.inventory);
    adjustTotalItems();
  }

  /*
   * Remove a specific number of items from the inventory and destroy.
   */
  public boolean consume(int quantity)
  {
    return this.remove(quantity, null);
  }

  /**
   * Return a reference to the first element to allow its contents to be
   * inspected. Does not remove from list.
   *
   * @return First element on list.
   */
  public Widget getFirst()
  {
    return inventory.getFirst();
  }

  /**
   * Return a reference to the first element to allow its contents to be
   * inspected. Does not remove from list.
   *
   * @return Last  element on list.
   */
  public Widget getLast()
  {
    return inventory.getLast();
  }

  /**
   * Return an iterator to allow operations on contents.
   *
   * @return listIterator Iterator
   */
  public Iterator<Widget> getIterator()
  {
    return inventory.listIterator();
  }

   /**
	 * Change ttl to new value. 
     * @param new ttl
     */
  public void setTTL(int ttl)
  {
	Iterator<Widget> iterator = inventory.iterator();
	while(iterator.hasNext())
	{
	   Widget w = iterator.next();
	   w.ttl = ttl;
	}
  }


  /**
   * Examine all widgets in inventory, decrease their ttl by 1, and remove 
   * those which have exceeded their time to live.
   */
  public void expire()
  {
//System.out.println("\n" + inventory.size());
	  Iterator<Widget> iterator = inventory.iterator();
	  while(iterator.hasNext())
	  {
		  Widget w = iterator.next();
		  if(--w.ttl <= 0)
		  {
			  iterator.remove();
		  }
	  }

//System.out.println(" " + inventory.size());
  }

  /**
   * Remove 1 item from the inventory and return in their own container.
   *
   * @return widget containing 1 item
   */

  public Widget remove()
  {

    return remove(1);
  }

  /**
   * Remove specific widget from inventory.
   *
   * @param widget  Widget to remove
   * @return widget or null if not found
   */
  public Widget remove(Widget widget)
  {
     if(inventory.remove(widget))
        return widget;
     else
        return null;
  }

  /**
   * Remove a specified number of items from the Inventory and return in
   * widget container. (Only works on objects without a TTL)
   *
   * @param quantity Number to remove
   * @return Widget containing # of items, or null if not available
   */

  public Widget remove(long quantity)
  {
    Inventory newinv = new Inventory(this.product, this.lifetime,
                                     this.unique);

    assert (!this.lifetime) : "Remove widget on Inventory with a TTL "
                              + product;

    if (!remove(quantity, newinv))
      return null;
    else
      return newinv.getFirst();
  }

  /**
   * Remove a specific number of items from the Inventory and return them in
   * newinv.
   *
   * @param quantity number of items to remove
   * @param newinv inventory container to return removed items in
   * @return t/f True if suceeds, otherwise false and no items removed
   */

  public boolean remove(long quantity, Inventory newinv)
  {
    if (quantity > this.getTotalItems())
    {
      // Todo: raise exception
      System.out
        .println("Error: Request to remove more than in inventory:"
                 + this.getTotalItems());
      System.out.println("      " + this.product + "(" + quantity + ")");
      return false;
    }
    else if (quantity == 0)
    {
      // Edge case - if there are no items in the inventory, and a
      // request to remove 0 items is received, we land here.
      System.out.println("Error: request to remove 0 items");
      return false;
    }
    else if (newinv == this)
    {
      System.out.println("newinv must be different inventory collection");
    }
    else
    {
      Widget w = inventory.removeFirst();

      if (w.quantity() == quantity)
      {
        if (newinv != null)
          newinv.add(w);

        adjustTotalItems();
        return true;
      }
      else if (w.quantity() > quantity)
      // Split into two widgets
      {
        if (newinv != null)
        {
          newinv.add(w.split(quantity));
        }
        else
          w.split(quantity);

        // Replace surplus items
        inventory.addFirst(w);
        adjustTotalItems();
        return true;
      }
      else if (w.quantity() < quantity)
      {
        if (newinv != null)
          newinv.add(w);

        remove(quantity - w.quantity(), newinv);
        adjustTotalItems();
      }
    }
    return true;
  }

  private int checkTotalSize()
  {
    int total = 0;

      for (Widget anInventory : inventory)
      {
          total += anInventory.quantity();
      }
    return total;
  }
}
