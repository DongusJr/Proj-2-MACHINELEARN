/* Program : Threadneedle
 *
 * Banks   : Container class for banks - all banks in single country banking
 *           system.
 * 
 * Author  : Jacky Mallett
 * Date    : November 2014
 *
 * Comments:
 */
package core;

import javafx.collections.*;

import java.util.HashMap;

public class Banks
{
  public CentralBank centralBank = null;
  private HashMap<String, Bank>       banklist;
  public  ObservableMap<String, Bank> obsBanks;

  /**
   * Constructor: create empty list of banks for a country.
   */

  public Banks()
  {
    banklist = new HashMap<>(20);
    obsBanks = FXCollections.observableMap(banklist);
  }

  public int noBanks()
  {
    return banklist.size();
  }

  /**
   * Return bank by name
   *
   * @param bankname Name of bank
   * @return bank
   */

  public Bank getBank(String bankname)
  {
    if ((centralBank != null) && centralBank.name.equals(bankname))
      return centralBank;
    else
      return banklist.get(bankname);
  }

  /**
   * Add a bank to the container, and initialise with central bank if
   * applicable. Central Banks are held separately to main list.
   *
   * @param bank Bank to add
   */
  public void addBank(Bank bank)
  {
    if (bank instanceof CentralBank)
      centralBank = (CentralBank) bank;
    else if (banklist.get(bank.name) == null)
      obsBanks.put(bank.name, bank);

    // Create a reserve account for the Bank at the central bank.
    if (centralBank != null)
      centralBank.addReserveAccount(bank);
  }

  /**
   * Add a central bank to the container. Provided in case later on it's
   * useful to track the central bank separately, at present does the same as
   * addBank()
   *
   * @param cb central bank
   */

  public void addCentralBank(CentralBank cb)
  {
    obsBanks.put(cb.name, cb);
  }

  /**
   * Return total # deposits in system.
   *
   * @return Total amount on deposit in commercial banks (not central bank)
   */

  public int getTotalBankDeposits()
  {
    int sum = 0;

    for (Bank b : banklist.values())
    {
      if (!(b instanceof CentralBank))
        sum += b.getTotalDeposits();
    }

    return sum;
  }

  public int getTotalBankLoans()
  {
    int sum = 0;

    for (Bank b : banklist.values())
    {
      if (!(b instanceof CentralBank))
        sum += b.getTotalLoans();
    }
    return sum;
  }

  /**
   * Return list of banks. This will allow smaller groups of banks to formed
   * under a single government if necessary later.
   *
   * @return banklist List of banks
   */

  public HashMap<String, Bank> getBankList()
  {
    return banklist;
  }

  /**
   * Clear banks for reset.
   */
  public void clear()
  {
    banklist.clear();
  }

  /**
   * Print list of all banks.
   */

  public void report()
  {
    System.out.println("Banks Report:");
    System.out.println("Central Bank: " + centralBank);
    banklist.values().forEach(System.out::println);
  }
}
