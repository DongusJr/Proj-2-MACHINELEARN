/* Program : Threadneedle
 *
 * CompanyRegistry   : Container class for companies - all companies in single country banking
 *           system.
 * 
 * Author  : Anton Laukemper
 * Date    : November 2016
 *
 * Comments:
 */
package core;

import javafx.collections.*;

import java.util.HashMap;

public class CompanyRegistry
{
  private HashMap<String, Company>       companyList;
  public  ObservableMap<String, Company> obsCompanies;

  /**
   * Constructor: create empty list of companies for a country.
   */

  public CompanyRegistry()
  {
    companyList = new HashMap<>(20);
    obsCompanies = FXCollections.observableMap(companyList);
  }
  
  /**
   * Return number of Companies
   *
   * 
   * @return number of Companies
   */
  
  

  public int numberOfCompanies()
  {
    return companyList.size();
  }

  /**
   * Return bank by name
   *
   * @param companyName Name of company
   * @return company
   */

  public Company getCompany(String companyName)
  {
      return companyList.get(companyName);
  }

  /**
   * Add a company to the container, and initialise with central company if
   * applicable. Central companys are held separately to main list.
   *
   * @param company company to add
   */
  public void addCompany(Company company)
  {
    if (companyList.get(company.name) == null)
      obsCompanies.put(company.name, company);

  }



  /**
   * Return list of companies. This will allow smaller groups of companies to formed
   * under a single government if necessary later.
   *
   * @return companyList List of companies
   */

  public HashMap<String, Company> getCompanyList()
  {
    return companyList;
  }

  /**
   * Clear companies for reset.
   */
  public void clear()
  {
    companyList.clear();
  }

  /**
   * Print list of all banks.
   */

  public void report()
  {
    System.out.println("Companies Report:");
    companyList.values().forEach(System.out::println);
  }
}
