package gui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.collections.*;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.*;

import java.net.URL;
import java.util.*;
import java.io.*;

import core.*;

public class TransactionView extends Stage implements Initializable
{
  @FXML ListView<String> transactions;

  ObservableList<String> items = FXCollections.observableArrayList();
  Ledger ledger; // Ledger being displayed

  public TransactionView(Ledger ledger)
  {
    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(
      "../../resources/transactionview.fxml"));
    fxmlLoader.setController(this);

    this.ledger = ledger;

    try
    {
      setScene(new Scene(fxmlLoader.load()));
	  // Ledgers may be individual accounts, or collections of
	  // accounts i.e. deposit account holders. In the latter case
	  // there isn't an account id.

	  if(ledger.getAccountNo() == -1)
		  this.setTitle(ledger.name + " Transactions");
	  else
          this.setTitle(ledger.name + "(" + ledger.getAccountNo() + ") Transactions");
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }

  }

  // TODO: convert to observable list to allow real time update
  @Override
  public void initialize(URL url, ResourceBundle resourceBundle)
  {
    transactions.setItems(items);
    for (Transaction t : ledger.transactions)
    {
      items.add(t.toString());
    }
//	System.out.println(ledger.transactions.size());
  }

  @FXML void onOkButton(ActionEvent event)
  {
    close();
  }
}
