// Program : Threadneedle
//
// Employee: Container class for Persons that allows them to use the 
//           Inventory class as a holding framework (for the Markets.)
//           Each employee holds one person.
// 
// Author  : Jacky Mallett
// Date    : April    2012
//
// Comments:
// Todo:     timestamp automatically
package core;

class Employee extends Widget
{
  Person person;

  public Employee(Person p, long desiredsalary, int lifetime, int created)
  {
    super("Labour", lifetime);
    this.person = p;
    this.person.desiredSalary = desiredsalary;
  }

  /**
   * Compare Employee containers based on desired salary for market sorting.
   *
   * @param employee Employee to compare with.
   * @return Returns: 0 desired salaries are identical +ve this salary is
   * greater than supplied -ve this salary is less than supplied
   */

  public long compareTo(Employee e)
  {
    return this.person.desiredSalary - e.person.desiredSalary;
  }

  /**
   * Each sub-class of widget has to implement it's own version of split() as
   * the generic Inventory can't create new instances of E, & in this case we
   * shouldn't split people. (Child processes will be handled separately.)
   * Todo: refactor into factory
   *
   * @param newQ new quantity.
   * @return Shares representing quantity requested removed from this
   * instance.
   */
  @Override
  public Employee split(long newQ)
  {
    throw new RuntimeException(
      "Split called on Employee (Cannot splinch people, it's cruel)");
  }

  /*
   * Override equals. Widgets are equal if name, id, created and and lifetime
   * are the same.
   *
   * @param obj object for comparison
   */
  public boolean equals(Object obj)
  {
    if (obj instanceof Employee)
    {
      Employee rhs = (Employee) obj;

      return (this.person == rhs.person);
    }
    return false;
  }

  public String toString()
  {
    return person.toString();
  }
}
