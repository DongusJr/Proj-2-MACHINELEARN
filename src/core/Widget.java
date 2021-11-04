// Program : Threadneedle
//
// Widget  : Base class for items produced within the simulation
// 
// Author  : Jacky Mallett
// Date    : March    2012
//
// Comments:
//           Widgets should have input associated defining how they're created
// Todo:     timestamp automatically
//           capture cost to manufacture

package core;

import base.*;

public class Widget implements Comparable<Widget>
{
  String name;                  // Label to identify widget
  protected int wid;            // unique widget id, used to identify object
  boolean consumable;           // widget can be destroyed
  int     created;              // Simulation time point it was created
  int     ttl;                  // Time to Live (-1 indestructible)
  public long quantity;         // identical widgets can be stacked
  public long lastSoldPrice;    // Price the widget last sold for.
  public int  lastSoldWhen;     // tic the price was set in

  long price;                   // current price (for non-adjusting markets)
  Agent owner;

  public Widget(String name, int lifetime, long quantity)
  {
    this.name = name;
    this.wid = Base.assignWidgetID();
    this.created = Base.step;
    this.ttl = lifetime * Base.Time.MONTH.period();
    this.quantity = quantity;

    if (this.quantity <= 0)
      throw new RuntimeException("Incorrect quantity for widget "
                                 + this.quantity);

  }

  public Widget(String name, int lifetime)
  {
    this(name, lifetime, 1);
  }

  /**
   * Constructor which splits out a new Widget container from an existing one,
   * placing part of the existing widgets contents into the new widget.
   *
   * @param name     Label identifying widget
   * @param lifetime time to live
   * @param w        existing widget to take new contents from
   * @param newQ     quantity to put into this widget
   */

  public Widget(String name, int lifetime, Widget w, long newQ)
  {
    this(name, lifetime, newQ);

    w.quantity -= newQ;

    if ((w.quantity <= 0) || (newQ <= 0))
      throw new RuntimeException("Incorrect quantity for widget "
                                 + w.quantity + " newQ: " + newQ);
  }

  @Override
  public int compareTo(Widget otherWidget)
  {
     return Long.compare(price, otherWidget.price);
  }

  /*
   * Split this widget into two identical widgets, returning a new widget
   * having quantity items, and removing those items from this one.
   */
  public Widget split(long newQ)
  {
    if (this.quantity < newQ)
    {
      throw new RuntimeException("Insufficient quantity for split: "
                                 + this.quantity + " < " + newQ);
    }
    else
    {
      Widget w = new Widget(name, ttl, newQ);
      w.created = this.created;

      this.quantity -= newQ;

      return w;
    }
  }

  /**
   * Override equals. Widgets are equal if name, id, created and and lifetime
   * are the same.
   *
   * @param obj Widget to compare
   */
  public boolean equals(Object obj)
  {
    if (obj instanceof Widget)
    {
      Widget rhs = (Widget) obj;

      return ((this.name.equals(rhs.name)) && (this.ttl == rhs.ttl)
              && (this.wid == rhs.wid) && (this.created == rhs.created) && (this.quantity == rhs.quantity));
    }
    return false;
  }


  /**
   * Return ttl status as a t/f flag.
   *
   * @return t/f True if has TTL, false if indestructible
   */
  public boolean hasTTL()
  {
    return ttl != -1;
  }

  /**
   * Merge widgets.
   *
   * @param w to merge into this one.
   */
  public void merge(Widget w)
  {
    /*
     * Check that widget isn't a class of person. If it is, then the unique
		 * flag should be set on the Inventory containing it to prevent this
		 * error.
		 */
    if (w instanceof Employee)
      throw new RuntimeException("People can't be merged");

    if (this == w)
      throw new RuntimeException("Merging derselbe widget: " + w + " "
                                 + this);
    if ((this.name.equals(w.name)) && (this.ttl == w.ttl))
    {
      this.quantity += w.quantity;

      // In certain other languages we would delete widget here.
      w.quantity = 0;
    }
    else
    {
      throw new RuntimeException("Attempt to merge un-identical widgets "
                                 + (name.equals(w.name) ? "" : name + "!=" + w.name)
                                 + (ttl == w.ttl ? "" : ttl + "!=" + w.ttl));
    }
  }

  /**
   * Getter for quantity contained by widget
   *
   * @return quantity of widget
   */
  public long quantity()
  {
    return this.quantity;
  }

  /**
   * Provide hash for widget.
   *
   * @return hash
   */

  public int hashCode()
  {
    int hash = 1;

    hash = hash * 31 + name.hashCode();
    hash = hash * 31 + wid;
    hash = hash * 31 + created;
    hash = hash * 31 * ttl;

    return hash;
  }

  public String toString()
  {
    return this.name + " : " + this.quantity + " (Created: "
           + this.created + " / " + "Lifetime: " + this.ttl + ")";
  }
}
