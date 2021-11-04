/* Program  : Threadneedle
 *
 * PreferentialShares: Container for preferential shares 
 *            Preferential shares provide the same interface as Shares,
 *            but exist as a separate type in order to allow their
 *            issuers to treat them differently wrt to dividends, 
 *            ownership rights etc.
 *
 * 
 * Author   : Jacky Mallett
 * Date     : December 2014
 * Comments :
 */

package core;

import java.util.*;

public class PreferentialShares extends Shares
{
  public char shareType = 'A';        // Type that can be assigned to share

  public PreferentialShares(String name, long quantity, long issuePrice,
                            Company issuer, LinkedList<Shares> masterList)
  {
    super(name, quantity, issuePrice, issuer, masterList);
  }

}
