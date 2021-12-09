package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.TransferMoneyException;
import com.db.awmd.challenge.repository.AccountsRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class AccountsService {

  @Getter
  private final AccountsRepository accountsRepository;

  private final EmailNotificationService notificationService;

  @Autowired
  public AccountsService(AccountsRepository accountsRepository,
                         EmailNotificationService notificationService) {
    this.accountsRepository = accountsRepository;
    this.notificationService = notificationService;
  }

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  public void transfer(String sourceAccountId, String targetAccountId, BigDecimal amount) {
    verifyAccountIds(sourceAccountId, targetAccountId);

    Account sourceAccount = getAccountById(sourceAccountId);
    Account targetAccount = getAccountById(targetAccountId);

    boolean sourceWithdraw = false;
    boolean targetDeposit = false;
    try {
      if (sourceAccount.withdraw(amount)) {
        sourceWithdraw = true;
        if (targetAccount.deposit(amount)) {
          targetDeposit = true;
          notificationService.notifyAboutTransfer(sourceAccount, "Withdrawing " + amount + " from the account");
          notificationService.notifyAboutTransfer(targetAccount, "Depositing " + amount + " to the account");
        } else {
          throwTransferException(sourceAccountId, targetAccountId);
        }
      } else {
        throwTransferException(sourceAccountId, targetAccountId);
      }
    } finally {
      if (sourceWithdraw && !targetDeposit) {
        sourceAccount.deposit(amount);
      }
    }
  }

  private void verifyAccountIds(String sourceAccountId, String targetAccountId) {
    if (sourceAccountId.equals(targetAccountId)) {
      throw new TransferMoneyException(
              String.format("Accounts for transferring money must be different: " +
                              "sourceAccountId = %s, targetAccountId = %s",
                      sourceAccountId, targetAccountId));
    }
  }

  private Account getAccountById(String accountId) {
    Account account = getAccount(accountId);
    if (account == null) {
      throw new TransferMoneyException(String.format("Account id = %s not found!", accountId));
    }
    return account;
  }

  private void throwTransferException(String sourceAccountId, String targetAccountId) {
    throw new TransferMoneyException(
            String.format("Failed to transfer money between accounts: " +
                            "sourceAccountId = %s, targetAccountId = %s",
                    sourceAccountId, targetAccountId));
  }
}
