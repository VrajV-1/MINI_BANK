package minibank;

import minibank.exception.AccountLockedException;
import minibank.exception.BankException;
import minibank.exception.InsufficientFundsException;
import minibank.exception.InvalidAmountException;
import minibank.exception.ValidationException;
import minibank.model.Account;
import minibank.model.Customer;
import minibank.model.CurrentAccount;
import minibank.model.FixedDepositAccount;
import minibank.model.PremiumAccount;
import minibank.model.SavingsAccount;
import minibank.model.WithdrawRule;
import minibank.repository.Repository;
import minibank.service.BankService;
import minibank.service.BankService.AccountType;
import minibank.util.CommandParser;
import minibank.util.BankUtils;
import minibank.util.ReportGenerator;
import minibank.util.StatePersister;
import minibank.util.TransactionLog;
import minibank.util.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankServiceTest {
    @TempDir
    Path temporaryDirectory;

    private Repository<Account> accounts;
    private Repository<Customer> customers;
    private TransactionLog log;
    private BankService bank;

    @BeforeEach
    void setUp() throws IOException {
        accounts = new Repository<>();
        customers = new Repository<>();
        log = new TransactionLog(temporaryDirectory);
        WithdrawRule rule = (account, amount) -> account.isActive()
                && amount > 0 && account.canWithdraw(amount);
        bank = new BankService(accounts, customers, log, rule);
    }

    @Test
    void opensAndFindsCustomerAccount() throws Exception {
        Account account = open("Asha", AccountType.SAVINGS, 1000, 100, 0);

        assertTrue(account.getAccountNumber().matches("AC[0-9]{4}"));
        assertEquals("Asha", account.getOwnerName());
        assertEquals(account, accounts.findById(account.getAccountNumber()));
        assertEquals(account.getOwner(),
                customers.findById(account.getOwner().getCustomerId()));
    }

    @Test
    void validatesCustomerAndCommandInput() throws Exception {
        assertFalse(Validator.isValidMobile("12345"));
        assertTrue(Validator.isValidMobile("9876543210"));
        assertTrue(Validator.isValidEmail("asha@example.com"));
        assertTrue(Validator.isValidPan("ABCDE1234F"));
        assertTrue(Validator.isValidIfsc("SBIN0001234"));
        assertEquals(500, CommandParser.parse("DEPOSIT AC0001 500").amount());
        assertEquals("AC0002",
                CommandParser.parse("TRANSFER AC0001 AC0002 200")
                        .destinationAccountNumber());
        assertThrows(InvalidAmountException.class,
                () -> CommandParser.parse("DEPOSIT AC0001 0"));
        assertThrows(ValidationException.class,
                () -> bank.openAccount(" ", "bad-email", "123", "",
                        new Customer.Address("", "", ""), AccountType.SAVINGS,
                        100, 0, 0));
    }

    @Test
    void depositAndWithdrawalKeepBalanceEncapsulated() throws Exception {
        Account account = open("Ravi", AccountType.SAVINGS, 5000, 500, 0);

        bank.deposit(account.getAccountNumber(), 2000);
        bank.withdraw(account.getAccountNumber(), 3000);

        assertEquals(4000, account.getBalance());
    }

    @Test
    void rejectedWithdrawalsDoNotChangeBalance() throws Exception {
        Account account = open("Mina", AccountType.SAVINGS, 1000, 0, 0);

        InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> bank.withdraw(account.getAccountNumber(), 1500));

        assertEquals(500, exception.getShortfall());
        assertEquals(1000, account.getBalance());
        assertThrows(InvalidAmountException.class,
                () -> bank.deposit(account.getAccountNumber(), -1));
    }

    @Test
    void savingsMinimumBalanceIsIncludedInShortfall() throws Exception {
        Account account = open("Minimum", AccountType.SAVINGS, 1000, 200, 0);

        InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> bank.withdraw(account.getAccountNumber(), 900));

        assertEquals(100, exception.getShortfall());
    }

    @Test
    void eachAccountTypeUsesItsOwnInterestAndWithdrawalRule() throws Exception {
        Account savings = open("Savings", AccountType.SAVINGS, 1000, 200, 0);
        Account current = open("Current", AccountType.CURRENT, 500, 0, 300);
        Account fixed = open("Fixed", AccountType.FIXED_DEPOSIT, 2000, 0, 0);
        Account premium = open("Premium", AccountType.PREMIUM, 1500, 100, 0);

        assertEquals(4.0, savings.interestRate());
        assertEquals(0.0, current.interestRate());
        assertEquals(7.0, fixed.interestRate());
        assertEquals(5.0, premium.interestRate());
        assertInstanceOf(PremiumAccount.class, premium);
        assertTrue(current instanceof CurrentAccount);
        assertTrue(savings instanceof SavingsAccount);
        assertThrows(AccountLockedException.class,
                () -> bank.withdraw(fixed.getAccountNumber(), 100));
        bank.withdraw(current.getAccountNumber(), 700);
        assertEquals(-200, current.getBalance());
    }

    @Test
    void transfersAreRecordedAndKeepAccountsBalanced() throws Exception {
        Account from = open("From", AccountType.SAVINGS, 1000, 0, 0);
        Account to = open("To", AccountType.SAVINGS, 200, 0, 0);

        bank.transfer(from.getAccountNumber(), to.getAccountNumber(), 300);

        assertEquals(700, from.getBalance());
        assertEquals(500, to.getBalance());
        assertTrue(bank.statement(from.getAccountNumber()).contains("TRANSFER"));
        assertThrows(InsufficientFundsException.class,
                () -> bank.transfer(from.getAccountNumber(), to.getAccountNumber(), 900));
        assertEquals(700, from.getBalance());
        assertEquals(500, to.getBalance());
    }

    @Test
    void batchAndProducerConsumerProcessCommands() throws Exception {
        Account account = open("Batch", AccountType.SAVINGS, 1000, 0, 0);
        String number = account.getAccountNumber();

        List<String> batchResults = bank.processBatch(List.of(
                "DEPOSIT " + number + " 100",
                "DEPOSIT " + number + " 200"));
        List<String> queueResults = bank.processQueuedBatch(List.of(
                "WITHDRAW " + number + " 50",
                "DEPOSIT " + number + " 25"));

        assertEquals(2, batchResults.size());
        assertTrue(batchResults.stream().allMatch(value -> value.contains("completed")));
        assertEquals(2, queueResults.size());
        assertEquals(1275, account.getBalance());
    }

    @Test
    void concurrentOppositeTransfersCompleteWithoutDeadlock() throws Exception {
        Account first = open("First", AccountType.SAVINGS, 10000, 0, 0);
        Account second = open("Second", AccountType.SAVINGS, 10000, 0, 0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread one = new Thread(() -> transferRepeatedly(first, second, failure));
        Thread two = new Thread(() -> transferRepeatedly(second, first, failure));
        one.start();
        two.start();
        one.join(5000);
        two.join(5000);

        assertFalse(one.isAlive(), "First transfer thread should finish");
        assertFalse(two.isAlive(), "Second transfer thread should finish");
        assertNull(failure.get());
        assertEquals(20000, first.getBalance() + second.getBalance());
    }

    @Test
    void savesLoadsAndReportsTransactions() throws Exception {
        Account first = open("Persist", AccountType.SAVINGS, 1000, 0, 0);
        bank.deposit(first.getAccountNumber(), 500);
        bank.withdraw(first.getAccountNumber(), 100);

        Path accountFile = temporaryDirectory.resolve("accounts.dat");
        StatePersister.save(bank.listAccounts().toArray(Account[]::new), accountFile);
        Account[] loaded = StatePersister.load(accountFile);

        assertEquals(1, loaded.length);
        assertEquals(first.getAccountNumber(), loaded[0].getAccountNumber());
        assertEquals(1400, loaded[0].getBalance());
        assertEquals(first.getOwner(), loaded[0].getOwner());

        Path reportFile = temporaryDirectory.resolve("report.txt");
        String report = ReportGenerator.generate(log.getDirectory(), reportFile);
        assertTrue(report.contains("Total deposits: 500"));
        assertTrue(report.contains("Total withdrawals: 100"));
        assertTrue(report.contains("Net change: 400"));
        assertTrue(Files.exists(reportFile));
    }

    @Test
    void transferIsIncludedInBothSidesOfTheReport() throws Exception {
        Account from = open("Report from", AccountType.SAVINGS, 1000, 0, 0);
        Account to = open("Report to", AccountType.SAVINGS, 1000, 0, 0);
        bank.transfer(from.getAccountNumber(), to.getAccountNumber(), 250);

        String report = ReportGenerator.generate(log.getDirectory(),
                temporaryDirectory.resolve("transfer-report.txt"));

        assertTrue(report.contains("Total deposits: 250"));
        assertTrue(report.contains("Total withdrawals: 250"));
        assertTrue(report.contains("Net change: 0"));
    }

    @Test
    void reportRejectsMalformedTransactionRecords() throws Exception {
        Path logDirectory = temporaryDirectory.resolve("bad-logs");
        Files.createDirectories(logDirectory);
        Files.writeString(logDirectory.resolve("bad.log"), "DEPOSIT AC0001 not-a-number");

        assertThrows(IOException.class,
                () -> ReportGenerator.generate(logDirectory,
                        temporaryDirectory.resolve("bad-report.txt")));
    }

    @Test
    void customerCloneAndGenericRepositoryWork() throws Exception {
        Account account = open("Clone", AccountType.SAVINGS, 100, 0, 0);
        Customer original = account.getOwner();
        Customer clone = original.clone();
        Repository<Customer> customerRepository = new Repository<>();
        customerRepository.save(clone);

        assertEquals(original, clone);
        assertNotNull(customerRepository.findById(original.getCustomerId()));
        assertEquals(1, customerRepository.findAll().size());
        assertTrue(customerRepository.delete(original.getCustomerId()));
        assertEquals(0, customerRepository.size());
    }

    @Test
    void boundedAverageWorksWithNumberArrays() {
        assertEquals(20.0, BankUtils.average(new Integer[] {10, 20, 30}), 0.001);
        assertEquals(20.0, BankUtils.average(new Double[] {10.0, 30.0}), 0.001);
        assertThrows(IllegalArgumentException.class,
                () -> BankUtils.average(new Integer[0]));
    }

    private Account open(String name, AccountType type, long balance,
            long minimumBalance, long overdraft) throws Exception {
        String emailName = name.toLowerCase().replace(" ", "");
        return bank.openAccount(name, emailName + "@example.com",
                "9876543210", "", new Customer.Address("Main Road", "Anand", "388001"),
                type, balance, minimumBalance, overdraft);
    }

    private void transferRepeatedly(Account from, Account to,
            AtomicReference<Throwable> failure) {
        for (int i = 0; i < 200; i++) {
            try {
                bank.transfer(from.getAccountNumber(), to.getAccountNumber(), 1);
            } catch (BankException | IOException exception) {
                failure.compareAndSet(null, exception);
                return;
            }
        }
    }
}
