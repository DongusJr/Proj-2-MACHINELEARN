package gui;

import javafx.geometry.*;
import javafx.scene.layout.*;

import core.*;

// http://docs.oracle.com/javafx/2/fxml_get_started/fxml_tutorial_intermediate.htm#CACFEHBI

/**
 * Display the General ledger for a single bank.
 */

public class BankView extends HBox
{
  Bank bank;
  LedgerView assets      = null;
  LedgerView liabilities = null;

  public BankView(Bank b)
  {
    this.bank = b;

    setPadding(new Insets(5, 5, 5, 5));

    int maxRows = Math.max(bank.gl.assets.size(),
                           bank.gl.liabilities.size() + bank.gl.equities.size());

    assets = new LedgerView(b, AccountType.ASSET, maxRows);
    setHgrow(assets, Priority.ALWAYS);
    this.getChildren().addAll(assets);

    liabilities = new LedgerView(b, AccountType.LIABILITY, maxRows);
    setHgrow(liabilities, Priority.ALWAYS);
    this.getChildren().addAll(liabilities);
  }

  public void refresh()
  {
    assets.refresh();
    liabilities.refresh();
  }
}
