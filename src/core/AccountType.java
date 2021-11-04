/* Program :   
 *           
 *           Double entry book keeping - Account classification.
 * 
 * Author  : Jacky Mallett
 * Date    : November 2014 
 *
 * Comment : Polarity provides the credit action on the account as per  
 *           the following table:
 *
 *			            Credit	Debit
 *           Liability    +1     -1
 *           Asset        -1     +1
 *           Equity       +1     -1
 *           
 */
package core;

public enum AccountType
{
  ASSET(-1),
  LIABILITY(+1),
  EQUITY(+1);

  private final int polarity;

  AccountType(int polarity)
  {
    this.polarity = polarity;
  }

  int polarity()
  {
    return polarity;
  }

}
