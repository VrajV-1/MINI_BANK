package minibank.service;

import minibank.exception.AccountNotFoundException;
import minibank.exception.BankException;
import minibank.exception.InactiveAccountException;
import minibank.exception.InvalidAmountException;
import minibank.exception.ValidationException;
import minibank.model.Account;
import minibank.model.CurrentAccount;
import minibank.model.Customer;
import minibank.model.FixedDepositAccount;
import minibank.model.PremiumAccount;
import minibank.model.SavingsAccount;
import minibank.model.TransactionType;
import minibank.model.WithdrawRule;
import minibank.repository.Repository;
import minibank.util.Command;
import minibank.util.CommandParser;
import minibank.util.AnnotationValidator;
import minibank.util.BankUtils;
import minibank.util.ReportGenerator;
import minibank.util.StatePersister;
import minibank.util.StatementFormatter;
import minibank.util.TransactionLog;
import minibank.util.Validator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

public class BankService {
    private final Repository<Account> accounts;
    private final Repository<Customer> customers;
    private final TransactionLog transactionLog;
    private final WithdrawRule withdrawRule;
    private final List<String> transactionHistory;

    public BankService(Repository<Account> accounts, Repository<Customer> customers,
            TransactionLog transactionLog, WithdrawRule withdrawRule) throws IOException {
        this.accounts = Objects.requireNonNull(accounts, "Account repository is required");
        this.customers = Objects.requireNonNull(customers, "Customer repository is required");
        this.transactionLog = Objects.requireNonNull(transactionLog,
                "Transaction log is required");
        this.withdrawRule = Objects.requireNonNull(withdrawRule,
                "Withdrawal rule is required");
        this.transactionHistory = new ArrayList<>(transactionLog.readAll());
        for (Account account : accounts.findAll()) {
            customers.save(account.getOwner());
            Customer.observeCustomerId(account.getOwner().getCustomerId());
        }
    }

    public Account openAccount(String name, String email, String mobile, String pan,
            Customer.Address address, AccountType type, long openingBalance,
            long minimumBalance, long overdraftLimit) throws BankException, IOException {
        if (openingBalance < 0) {
            throw new InvalidAmountException("Opening balance cannot be negative");
        }
        Customer customer = new Customer(name, email, mobile, pan, address);
        Validator.validateCustomer(customer);
        Account account;
        try {
            account = switch (type) {
                case SAVINGS -> new SavingsAccount(customer, openingBalance, minimumBalance);
                case CURRENT -> new CurrentAccount(customer, openingBalance, overdraftLimit);
                case FIXED_DEPOSIT -> new FixedDepositAccount(customer, openingBalance);
                case PREMIUM -> new PremiumAccount(customer, openingBalance, minimumBalance);
            };
        } catch (IllegalArgumentException exception) {
            throw new InvalidAmountException(exception.getMessage());
        }
        List<String> accountErrors = List.of(AnnotationValidator.validate(account));
        if (!accountErrors.isEmpty()) {
            throw new ValidationException(accountErrors);
        }
        record("OPEN " + account.getAccountNumber() + " " + openingBalance);
        customers.save(customer);
        accounts.save(account);
        return account;
    }

    public void deposit(String accountNumber, long amount) throws BankException, IOException {
        Account account = findAccount(accountNumber);
        synchronized (account) {
            account.validateDeposit(amount);
            record("DEPOSIT " + accountNumber + " " + amount);
            account.deposit(amount);
        }
    }

    public void withdraw(String accountNumber, long amount) throws BankException, IOException {
        Account account = findAccount(accountNumber);
        synchronized (account) {
            if (!account.isActive()) {
                throw new InactiveAccountException(accountNumber);
            }
            if (!withdrawRule.allow(account, amount)) {
                account.validateWithdrawal(amount);
                throw new BankException("Withdrawal is not allowed by the bank rule");
            }
            account.validateWithdrawal(amount);
            record("WITHDRAW " + accountNumber + " " + amount);
            account.withdraw(amount);
        }
    }

    public void transfer(String fromNumber, String toNumber, long amount)
            throws BankException, IOException {
        if (fromNumber.equals(toNumber)) {
            throw new BankException("Choose two different accounts for a transfer");
        }
        Account from = findAccount(fromNumber);
        Account to = findAccount(toNumber);
        Account first = from.compareTo(to) < 0 ? from : to;
        Account second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (!from.isActive()) {
                    throw new InactiveAccountException(fromNumber);
                }
                if (!to.isActive()) {
                    throw new InactiveAccountException(toNumber);
                }
                if (!withdrawRule.allow(from, amount)) {
                    from.validateWithdrawal(amount);
                    throw new BankException("Transfer is not allowed by the bank rule");
                }
                from.validateWithdrawal(amount);
                to.validateDeposit(amount);
                String transferLine = "TRANSFER " + fromNumber + " "
                        + toNumber + " " + amount;
                record(transferLine);
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }

    public List<String> processBatch(List<String> commands) throws BankException {
        Objects.requireNonNull(commands, "Commands are required");
        List<Future<String>> tasks = new ArrayList<>();
        List<String> results = new ArrayList<>();
        try (TransactionProcessor processor = new TransactionProcessor()) {
            for (String command : commands) {
                tasks.add(processor.submit(() -> processSafely(command)));
            }
            processor.stop();
            for (Future<String> task : tasks) {
                results.add(TransactionProcessor.getResult(task));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BankException("Batch processing was interrupted", exception);
        } catch (TransactionProcessor.BatchFailure exception) {
            throw new BankException("Batch processing failed", exception.getCause());
        }
        return results;
    }

    public List<String> processQueuedBatch(List<String> commands) throws BankException {
        Objects.requireNonNull(commands, "Commands are required");
        try {
            return TransactionProcessor.processWithQueue(commands, this::processSafely);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BankException("Queued transaction processing was interrupted",
                    exception);
        }
    }

    private String processSafely(String line) {
        try {
            Command command = CommandParser.parse(line);
            if (command.type() == TransactionType.DEPOSIT) {
                deposit(command.accountNumber(), command.amount());
                return "Deposit completed: " + command.accountNumber();
            }
            if (command.type() == TransactionType.WITHDRAW) {
                withdraw(command.accountNumber(), command.amount());
                return "Withdrawal completed: " + command.accountNumber();
            }
            transfer(command.accountNumber(), command.destinationAccountNumber(),
                    command.amount());
            return "Transfer completed: " + command.accountNumber()
                    + " to " + command.destinationAccountNumber();
        } catch (BankException | IOException exception) {
            return "Transaction rejected: " + exception.getMessage();
        }
    }

    public String statement(String accountNumber) throws AccountNotFoundException {
        return StatementFormatter.buildStatement(findAccount(accountNumber),
                historySnapshot());
    }

    public List<Account> listAccounts() {
        List<Account> result = new ArrayList<>(accounts.findAll());
        Map<String, Long> balanceSnapshot = new HashMap<>();
        for (Account account : result) {
            balanceSnapshot.put(account.getAccountNumber(), account.getBalance());
        }
        result.sort(Comparator.comparingLong(
                        (Account account) -> balanceSnapshot.get(account.getAccountNumber()))
                .reversed()
                .thenComparing(Account::getAccountNumber));
        return result;
    }

    public List<Account> searchByOwnerName(String name) {
        String query = name.toLowerCase(java.util.Locale.ROOT);
        return accounts.findAll().stream()
                .filter(account -> account.getOwnerName()
                        .toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Account::getOwnerName)
                        .thenComparing(Account::getAccountNumber))
                .toList();
    }

    public double yearlyInterest(String accountNumber) throws AccountNotFoundException {
        return findAccount(accountNumber).yearlyInterest();
    }

    public double averageBalance() {
        List<Account> allAccounts = accounts.findAll();
        if (allAccounts.isEmpty()) {
            return 0;
        }
        Long[] balances = allAccounts.stream().map(Account::getBalance)
                .toArray(Long[]::new);
        return BankUtils.average(balances);
    }

    public String createReport(Path reportFile) throws IOException {
        return ReportGenerator.generate(transactionLog.getDirectory(), reportFile);
    }

    public void saveState(Path file) throws IOException {
        StatePersister.save(listAccounts().toArray(Account[]::new), file);
    }

    public int accountCount() {
        return accounts.size();
    }

    private Account findAccount(String accountNumber) throws AccountNotFoundException {
        Account account = accounts.findById(accountNumber);
        if (account == null) {
            throw new AccountNotFoundException(accountNumber);
        }
        return account;
    }

    private synchronized void record(String line) throws IOException {
        transactionLog.append(line);
        transactionHistory.add(line);
    }

    private synchronized List<String> historySnapshot() {
        return new ArrayList<>(transactionHistory);
    }

    public enum AccountType {
        SAVINGS,
        CURRENT,
        FIXED_DEPOSIT,
        PREMIUM
    }
}
