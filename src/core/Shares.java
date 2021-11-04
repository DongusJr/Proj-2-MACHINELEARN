/* Program  : Threadneedle
 *
 * Share    : Container for shares 
 *
 * 
 * Author   : Jacky Mallett
 * Date     : October 2012
 * Comments :
 */

package core;

import java.util.LinkedList;

public class Shares extends Widget
{
   long issuePrice; // Price shares are issued at
   Company issuer; // Company which issued shares.

   public Agent getOwner() {
     return owner;
   }

  Agent owner; // Owner of shares (can be company or person)

  /* List of all shares issued by company - updated by Shares when created, 
     split and merged. */
  LinkedList<Shares> masterList;
  /**
   * Constructor:
   *
   * @param name             Symbol for share
   * @param quantity         Number to create
   * @param issuePrice       Amount issued for
   * @param issuer           Company issuing shares
   * @param masterList       list of all shares
   */

   public Shares(String name, long quantity, long issuePrice, Company issuer,
                 LinkedList<Shares> masterList)
   {
        super(name, -1, quantity);

        if (this.quantity <= 0)
            throw new RuntimeException("Invalid quantity of Shares: "+quantity);

        this.issuePrice = issuePrice;
        this.issuer = issuer;
        this.masterList = masterList;
        this.owner = issuer; // Default owner of share is issuer

        masterList.add(this);
   }

   /**
    * Constructor: Used internally when widgets are being split apart, in order
    * that quantity manipulations are only done in the base class
    *
    * @param name            Symbol for share
    * @param quantity        Number to create
    * @param issuePrice      Amount issued for
    * @param issuer          company issuing shares
    * @param s               Shares being derived from
    * @param masterList      masterlist of shares
    * @param newQ            new quantity
    */
    public Shares(String name, long quantity, long issuePrice, Company issuer,
                  LinkedList<Shares> masterList, Shares s, long newQ)
    {
        super(name, -1, s, newQ);

		if ((this.quantity <= 0) || (newQ <= 0))
			throw new RuntimeException("Invalid quantity of Shares: "
					+ quantity);

		this.issuePrice = issuePrice;
		this.issuer = issuer;
		this.masterList = masterList;
		masterList.add(this);
	}

	/**
	 * Has share been sold to an investor?
	 *
	 * @return t/f owner != issuer
	 */

	public boolean issued()
	{
		return owner != issuer;
	}

	/**
	 * Transfer ownership of shares.
	 *
	 * @param newowner Newowner for the shares
	 */

	public void transfer(Agent newowner)
	{
		owner = newowner;
		newowner.addInvestment(this);
	}

	/**
	 * Get the dividend payment that would be required at the supplied rate.
	 *
	 * @param percent
	 *            Percentage payout of issuePrice. (expressed as whole integer
	 *            i.e. 10%)
	 *
	 * @return amount to pay
	 */

	public double getDividend(double percent)
	{
		System.out.println("div: " + this.quantity + " " + percent + " "
	    		+ this.issuePrice + " owned by: " + this.owner.name);

		return (100 * this.quantity * percent * this.issuePrice) / 100.0;
	}

	/**
	 * Each sub-class has to implement it's own version of split() as the
	 * generic Inventory can't create new instances of E.
	 *
	 * @param newQ
	 *            new quantity.
	 *
	 * @return Shares representing quantity requested removed from this
	 *         instance.
	 */
	@Override
	public Shares split(long newQ)
	{
		if ((this.quantity() < newQ) || (newQ == 0))
		{
			throw new RuntimeException("Insufficient quantity for split: "
					+ this.quantity() + "<" + newQ);
		} else
		{
			Shares w = new Shares(name, newQ, issuePrice, this.issuer,
					this.masterList, this, newQ);

			w.created = this.created;
			w.owner = this.owner;

//			this.quantity -= newQ; // I think this is a bug, see Widget.java:60

			return w;
		} // Comment: what happens when this.quantity becomes 0? 
	}

	@Override
	public String toString()
	{
		return this.name + " : " + this.quantity + " Issuer : "
                + this.issuer + " Owner  : " + this.owner;
	}
}
