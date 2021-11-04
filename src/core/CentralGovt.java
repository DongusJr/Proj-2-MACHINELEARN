/* Program : Threadneedle
 *
 * CentralGovt    : Base class for CentralGovernment
 * 
 * Author  : Jacky Mallett
 * Date    : February 2012
 * Comments: There is an interesting and potentially simulation affecting issue
 *           of  whether the government has an account at the central bank, 
 *           or at a commercial bank. 
 *
 *           13/7 : FRB accounting doc. US Treasury keeps accounts at cb.
 */
package core;

import java.util.*;

public class CentralGovt extends Govt
{
  protected Bank bank = null; // Government's commercial bank (can be null)

  // Display money supply including interest income accounts.

  public boolean extendedMoneySupply = false;

  // Include non-cash income (icelandic lending)

  public boolean nonCashIncome = false;

  /**
   * Constructor.
   *
   * @param name name for Government (unique label)
   * @param x    ,y co-ordinates for display
   */

  CentralGovt(String name, String bankname, long initialDeposit)
  {
    super(name, bankname, initialDeposit);

    this.govt = this;
    this.Id = 0;                                 // Default Govt to 0 for now
    this.bankname = bankname;
  }

  CentralGovt()
  {
    this.Id = 0;
    this.govt = this;
  }

  public void evaluate(boolean report, int step)
  {
    super.evaluate(report, step);
  }

  /**
   * Get the total money supply for this system - defining money supply as
   * deposits at the commercial banks - money that can affect the price level.
   *
   * @return total deposit supply
   */

  @Override
  public long getDepositSupply()
  {
    return banks.getTotalBankDeposits();
  }


  /**
   * Get the capital limit if this applies - access to central bank info.
   *
   * @return capital limit across all banks
   */
  public int getCapitalLimit()
  {
    if (centralbank != null)
      return centralbank.getCapitalReserveLimit();
    else
      return 0;
  }

  /**
   * Return list of banks. This will allow smaller groups of banks to formed
   * under a single government if necessary later.
   *
   * @return banklist List of banks
   */

  public HashMap<String, Bank> getBankList()
  {
    return banks.getBankList();
  }

  /**
   * Return bank from name
   *
   * @param name Name of bank
   * @return bank Bank object or null
   */

  public Bank lookupBank(String name)
  {
    if (banks == null)
      System.out.println("** List of Banks has not been initialised **");

    return banks.getBank(name);
  }

  /**
   * stub for now - treasuries should be market traded.
   *
   * @param amount   Amount to pay
   * @param duration Period of treasury
   * @param a        Agent purchasing treasury
   * @param act      Account paying for treasury
   *
   * @return Treasury purchased or null
   */

  public Treasury buyTreasury(int amount, int duration, Agent a, Account act)
  {
    return null;
  }

  /**
   * Getter for non-cash income 
   *
   * return t/f
   */
  @Override
  public boolean getNonCashIncome()
  {
    return centralbank.nonCashIncome;
  }

  /**
   * Print report
   */

  public void print(String label)
  {
    System.out.println(label + ":" + name);
  }
}
