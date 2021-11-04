/* Program : Threadneedle
 *
 *           Accounting  exception
 *
 * Author  : (c) Jacky Mallett
 * Date    : November 2014 
 *
 */

package core;

/*
 * Turn off the serialVersionUID exception. Compiler will generate one 
 * automatically, and this can be used as a default. If and when versioning 
 * matters for distribution, this should be replaced.
 */
@SuppressWarnings("serial") class AccountingException extends Exception
{
  public AccountingException(core.Ledger debit, Ledger credit, String text)
  {
    super(text + " (" + debit.name + "," + credit.name + ")");
  }

  public AccountingException(String text)
  {
    super(text);
  }
}
