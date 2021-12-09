package com.db.awmd.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import static com.db.awmd.challenge.AccountsServiceTest.SOURCE_ACCOUNT_ID;
import static com.db.awmd.challenge.AccountsServiceTest.SOURCE_ACCOUNT_BALANCE;
import static com.db.awmd.challenge.AccountsServiceTest.TARGET_ACCOUNT_ID;
import static com.db.awmd.challenge.AccountsServiceTest.TARGET_ACCOUNT_BALANCE;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.service.AccountsService;
import java.math.BigDecimal;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest
@WebAppConfiguration
public class AccountsControllerTest {

  private MockMvc mockMvc;

  @Autowired
  private AccountsService accountsService;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Before
  public void prepareMockMvc() {
    this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

    // Reset the existing accounts before each test.
    accountsService.getAccountsRepository().clearAccounts();
  }

  @Test
  public void createAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    Account account = accountsService.getAccount("Id-123");
    assertThat(account.getAccountId()).isEqualTo("Id-123");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createDuplicateAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNegativeBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountEmptyAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void getAccount() throws Exception {
    String uniqueAccountId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
      .andExpect(status().isOk())
      .andExpect(
        content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
  }

  @Test
  public void transfer() throws Exception {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    createAccount(TARGET_ACCOUNT_ID, TARGET_ACCOUNT_BALANCE);

    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":\"" + SOURCE_ACCOUNT_ID + "\"," +
                    "\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":150.55}"))
            .andExpect(status().isOk());

    Account sourceAccount = accountsService.getAccount(SOURCE_ACCOUNT_ID);
    assertThat(sourceAccount.getBalance()).isEqualTo(new BigDecimal("400.00"));

    Account targetAccount = accountsService.getAccount(TARGET_ACCOUNT_ID);
    assertThat(targetAccount.getBalance()).isEqualTo(new BigDecimal("550.80"));
  }

  @Test
  public void transferNegativeAmount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":\"" + SOURCE_ACCOUNT_ID + "\"," +
                    "\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":-10.50}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferEmptyAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":\"\"," +
                    "\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":10.50}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferNullAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":null," +
                    "\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":10.50}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferWrongBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":10.50}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferWrongAmountDigits() throws Exception {
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":\"" + SOURCE_ACCOUNT_ID + "\"," +
                    "\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":10.555}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferDuplicateAccount() throws Exception {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":\"" + SOURCE_ACCOUNT_ID + "\"," +
                    "\"targetAccountId\":\"" + SOURCE_ACCOUNT_ID + "\"," +
                    "\"amount\":120.34}"))
            .andExpect(status().isNotAcceptable())
            .andExpect(content().string("Accounts for transferring money must be different: " +
                    "sourceAccountId = " + SOURCE_ACCOUNT_ID + ", " +
                    "targetAccountId = " + SOURCE_ACCOUNT_ID));
  }

  @Test
  public void transferMissingAccount() throws Exception {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":\"" + SOURCE_ACCOUNT_ID + "\"," +
                    "\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":10.55}"))
            .andExpect(status().isNotAcceptable())
            .andExpect(content().string("Account id = " + TARGET_ACCOUNT_ID + " not found!"));
  }

  @Test
  public void transferNotEnoughBalanceAccount() throws Exception {
    createAccount(SOURCE_ACCOUNT_ID, SOURCE_ACCOUNT_BALANCE);
    createAccount(TARGET_ACCOUNT_ID, TARGET_ACCOUNT_BALANCE);
    this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceAccountId\":\"" + SOURCE_ACCOUNT_ID + "\"," +
                    "\"targetAccountId\":\"" + TARGET_ACCOUNT_ID + "\"," +
                    "\"amount\":750.34}"))
            .andExpect(status().isNotAcceptable())
            .andExpect(content().string("Failed to transfer money between accounts: " +
                    "sourceAccountId = " + SOURCE_ACCOUNT_ID + ", " +
                    "targetAccountId = " + TARGET_ACCOUNT_ID));
  }

  private void createAccount(String accountId, BigDecimal balance){
    Account sourceAccount = new Account(accountId);
    sourceAccount.setBalance(balance);
    accountsService.createAccount(sourceAccount);
  }

}
