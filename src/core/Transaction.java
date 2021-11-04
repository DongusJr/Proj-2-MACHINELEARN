/* Program : Threadneedle
 *
 * Transaction: Double entry transaction                                   
 * 
 * Author  : Jacky Mallett
 * Date    : July 2012     
 *
 * Comments:
 */

package core;

import base.Base;

public class Transaction
{
  String text;
  String    debitAccountId;
  String    creditAccountId;
  long   amount;
  int    time; // Step transaction occurred in

  public Transaction(String text, String debitAccountId, String creditAccountId,
                     long amount)
  {
    this.text = text;
    this.debitAccountId = debitAccountId;
    this.creditAccountId = creditAccountId;
    this.amount = amount;
    this.time = Base.step;
  }

  public String toString()
  {
    return time + " Debit: " + debitAccountId + ", Credit: "
           + creditAccountId + " Amount: " + amount + " [" + text + "]";
  }

  /**
   * Return a string representing a transaction, ordered on the supplied
   * account number.
   *
   * @param accountName Account id to order on
   * @return String for transaction.
   */
  public String toString(String accountName)
  {
    if (accountName.equals(debitAccountId))
      return this.toString();
    else if (accountName.equals(creditAccountId))
    {
      return "/" + time + " Credit: " + creditAccountId
             + ", Debit: " + debitAccountId + " Amount:" + amount
             + " [" + text + "]";
    }
    // Account isn't specified in transaction, just bounce back the string.
    else
      return toString();
  }

  /**
   * Return a comman separated header for a transaction file export (as below).
   *
   * @return csv header
   */

  public static String getCSVHeader()
  {
      return "step,ledger name, credit account ID, debit account ID, amount, text\n";
  }

  /**
   * Return a comma separated string representing a transaction, suitable for export 
   * to transaction csv file.
   *
   * @param ledger  Name of ledger this transaction belongs to.
   *
   * @return csv string
   */
  public String toCSVString(String ledger)
  {
      return time + "," + ledger + "," + creditAccountId + "," + debitAccountId + "," + amount + "," + text + "\n";
  }
}
