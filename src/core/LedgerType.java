/* Program : Monetary System
 *           
 *           Double entry book keeping - Type of instrument/money tracked
 *           by the ledger.
 * 
 * Author  : Jacky Mallett
 * Date    : July 2012     
 *
 *           Ledgers have a type which specifies what is in the account.
 */
package core;

public enum LedgerType
{
  LOAN,
  // Loan to the account holder
  CAPITAL,
  // Capital
  DEPOSIT,
  // Deposit - money on deposit
  CASH     // Physical money
}
