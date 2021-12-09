package com.db.awmd.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.exception.TransferMoneyException;
import java.math.BigDecimal;
import com.db.awmd.challenge.service.AccountsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountsServiceTest {

  @Autowired
  private AccountsService accountsService;

  public static String SOURCE_ACCOUNT_ID = "ID-1";
  public static BigDecimal SOURCE_ACCOUNT_BALANCE = new BigDecimal("550.55");

  public static String TARGET_ACCOUNT_ID = "ID-2";
  public static BigDecimal TARGET_ACCOUNT_BALANCE = new BigDecimal("400.25");

  @Before
  public void clearAccounts() {
    this.accountsService.getAccountsRepository().clearAccounts();
  }

  @Test
  public void addAccount() throws Exception {
    Account account = new Account("Id-123");
    account.setBalance(new BigDecimal(1000));
    this.accountsService.createAccount(account);

    assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
  }

  @Test
  public void addAccount_failsOnDuplicateId() throws Exception {
    String uniqueId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueId);
    this.accountsService.createAccount(account);

    try {
      this.accountsService.createAccount(account);
      fail("Should have failed when adding duplicate account");
    } catch (DuplicateAccountIdException ex) {
      assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
    }

  }

  @Test
  public void testTransfer() {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    createAccount(TARGET_ACCOUNT_ID, TARGET_ACCOUNT_BALANCE);

    accountsService.transfer(SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID, new BigDecimal("150.55"));

    assertThat(accountsService.getAccount(SOURCE_ACCOUNT_ID).getBalance()).isEqualTo(new BigDecimal("400.00"));
    assertThat(accountsService.getAccount(TARGET_ACCOUNT_ID).getBalance()).isEqualTo(new BigDecimal("550.80"));
  }

  @Test(expected = TransferMoneyException.class)
  public void testTransferFailsOnDuplicateId() {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    try {
      accountsService.transfer(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_ID, new BigDecimal("100.00"));
      fail("Should have failed when transferring from the same account");
    } catch (TransferMoneyException ex) {
      assertThat(ex.getMessage()).isEqualTo(
              "Accounts for transferring money must be different: " +
              "sourceAccountId = " + SOURCE_ACCOUNT_ID + ", " +
              "targetAccountId = " + SOURCE_ACCOUNT_ID);
      throw ex;
    }
  }

  @Test(expected = TransferMoneyException.class)
  public void testTransferFailsOnMissingAccount() {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    try {
      accountsService.transfer(SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID, new BigDecimal("500.00"));
      fail("Should have failed when transferring to the missing account");
    } catch (TransferMoneyException ex) {
      assertThat(ex.getMessage()).isEqualTo("Account id = " + TARGET_ACCOUNT_ID + " not found!");
      throw ex;
    }
  }

  @Test(expected = TransferMoneyException.class)
  public void testTransferFailsOnNotEnoughBalanceAccount() {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    createAccount(TARGET_ACCOUNT_ID, TARGET_ACCOUNT_BALANCE);
    try {
      accountsService.transfer(SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID, new BigDecimal("570.75"));
      fail("Should have failed when there are not enough funds in the account");
    } catch (TransferMoneyException ex) {
      assertThat(ex.getMessage()).isEqualTo("Failed to transfer money between accounts: " +
              "sourceAccountId = " + SOURCE_ACCOUNT_ID + ", " +
              "targetAccountId = " + TARGET_ACCOUNT_ID);
      throw ex;
    }
  }

  @Test(expected = TransferMoneyException.class)
  public void testTransferFailsOnDepositTargetAccount() {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    createAccount(TARGET_ACCOUNT_ID, TARGET_ACCOUNT_BALANCE);

    AccountsService spyAccountService = spy(accountsService);
    Account targetAccount = spy(accountsService.getAccount(TARGET_ACCOUNT_ID));

    when(spyAccountService.getAccount(TARGET_ACCOUNT_ID)).thenReturn(targetAccount);
    when(targetAccount.deposit(new BigDecimal("100.75"))).thenReturn(false);
    try {
      spyAccountService.transfer(SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID, new BigDecimal("100.75"));
      fail("Should have failed when deposit to target account return false");
    } catch (TransferMoneyException ex) {
      assertThat(accountsService.getAccount(SOURCE_ACCOUNT_ID).getBalance()).isEqualTo(SOURCE_ACCOUNT_BALANCE);
      assertThat(accountsService.getAccount(TARGET_ACCOUNT_ID).getBalance()).isEqualTo(TARGET_ACCOUNT_BALANCE);
      assertThat(ex.getMessage()).isEqualTo("Failed to transfer money between accounts: " +
              "sourceAccountId = " + SOURCE_ACCOUNT_ID + ", " +
              "targetAccountId = " + TARGET_ACCOUNT_ID);
      throw ex;
    }
  }

  @Test
  public void testTransferWithConcurrency() throws InterruptedException {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    createAccount(TARGET_ACCOUNT_ID, TARGET_ACCOUNT_BALANCE);

    int numberOfThreads = 1000;
    ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(numberOfThreads);

    for (int i = 0; i < numberOfThreads/2; i++) {
      service.execute(() -> {
        accountsService.transfer(SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID, new BigDecimal("0.5"));
        latch.countDown();
      });
    }
    for (int i = 0; i < numberOfThreads/2; i++) {
      service.execute(() -> {
        accountsService.transfer(TARGET_ACCOUNT_ID, SOURCE_ACCOUNT_ID, new BigDecimal("1"));
        latch.countDown();
      });
    }
    latch.await();

    BigDecimal expectedSourceAccBalance = SOURCE_ACCOUNT_BALANCE.subtract(new BigDecimal("250")).add(new BigDecimal("500"));
    BigDecimal expectedTargetAccBalance = TARGET_ACCOUNT_BALANCE.subtract(new BigDecimal("500")).add(new BigDecimal("250"));

    assertThat(accountsService.getAccount(SOURCE_ACCOUNT_ID).getBalance()).isEqualTo(expectedSourceAccBalance);
    assertThat(accountsService.getAccount(TARGET_ACCOUNT_ID).getBalance()).isEqualTo(expectedTargetAccBalance);
  }

  private void createAccount(String accountId, BigDecimal balance){
    Account sourceAccount = new Account(accountId);
    sourceAccount.setBalance(balance);
    accountsService.createAccount(sourceAccount);
  }

}
