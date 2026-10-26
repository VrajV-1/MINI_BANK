package minibank;

import minibank.exception.BankException;
import minibank.model.Account;
import minibank.model.Customer;
import minibank.model.WithdrawRule;
import minibank.repository.Repository;
import minibank.service.BankService;
import minibank.service.BankService.AccountType;
import minibank.util.StatePersister;
import minibank.util.TransactionLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static minibank.util.BankUtils.formatCurrency;

public class MiniBank {
    private static final Path DATA_DIRECTORY = Path.of("data");
    private static final Path ACCOUNTS_FILE = DATA_DIRECTORY.resolve("accounts.dat");
    private static final Path REPORT_FILE = DATA_DIRECTORY.resolve("report.txt");

    public record BankInfo(String name, String branch) {
    }

    private enum MenuOption {
        OPEN_ACCOUNT,
        DEPOSIT,
        WITHDRAW,
        TRANSFER,
        STATEMENT,
        LIST_ACCOUNTS,
        BATCH,
        QUEUED_BATCH,
        REPORT,
        INTEREST,
        EXIT
    }

    private static final class MenuSession implements AutoCloseable {
        void start() {
            System.out.println("MiniBank session started.");
        }

        @Override
        public void close() {
            System.out.println("Console session resource closed.");
        }
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Files.createDirectories(DATA_DIRECTORY);
        Repository<Account> accounts = new Repository<>();
        Repository<Customer> customers = new Repository<>();
        if (Files.exists(ACCOUNTS_FILE)) {
            for (Account account : StatePersister.load(ACCOUNTS_FILE)) {
                accounts.save(account);
                customers.save(account.getOwner());
            }
        }

        WithdrawRule basicRule = new WithdrawRule() {
            @Override
            public boolean allow(Account account, long amount) {
                return amount > 0 && account.canWithdraw(amount);
            }
        };
        WithdrawRule activeAccountRule =
                (account, amount) -> account.isActive() && basicRule.allow(account, amount);

        TransactionLog transactionLog = new TransactionLog(DATA_DIRECTORY);
        BankService bank = new BankService(
                accounts, customers, transactionLog, activeAccountRule);

        System.out.println("================================");
        System.out.println(new BankInfo("MiniBank", "CHARUSAT Branch"));
        System.out.println("================================");

        try (Scanner input = new Scanner(System.in); MenuSession session = new MenuSession()) {
            session.start();
            boolean running = true;
            while (running) {
                printMenu();
                int choice = readInteger(input, "Enter your choice: ");
                MenuOption option = menuOption(choice);
                if (option == null) {
                    System.out.println("Please choose a number from 1 to 11.");
                    continue;
                }
                try {
                    switch (option) {
                        case OPEN_ACCOUNT -> openAccount(input, bank);
                        case DEPOSIT -> deposit(input, bank);
                        case WITHDRAW -> withdraw(input, bank);
                        case TRANSFER -> transfer(input, bank);
                        case STATEMENT -> statement(input, bank);
                        case LIST_ACCOUNTS -> listAccounts(bank);
                        case BATCH -> processBatch(input, bank, false);
                        case QUEUED_BATCH -> processBatch(input, bank, true);
                        case REPORT -> createReport(bank);
                        case INTEREST -> showInterest(input, bank);
                        case EXIT -> {
                            bank.saveState(ACCOUNTS_FILE);
                            System.out.println("Accounts saved. Thank you for using MiniBank.");
                            running = false;
                        }
                    }
                } catch (BankException | IOException exception) {
                    System.out.println("Operation failed: " + exception.getMessage());
                } catch (IllegalArgumentException exception) {
                    System.out.println("Invalid input: " + exception.getMessage());
                }
            }
        } finally {
            System.out.println("MiniBank session ended.");
        }
    }

    private static void printMenu() {
        System.out.println("\n----- MiniBank Menu -----");
        System.out.println("1. Open account");
        System.out.println("2. Deposit");
        System.out.println("3. Withdraw");
        System.out.println("4. Transfer");
        System.out.println("5. View account statement");
        System.out.println("6. List accounts");
        System.out.println("7. Process batch with thread pool");
        System.out.println("8. Process batch with producer-consumer");
        System.out.println("9. Create transaction report");
        System.out.println("10. View yearly interest");
        System.out.println("11. Save and exit");
    }

    private static MenuOption menuOption(int choice) {
        return switch (choice) {
            case 1 -> MenuOption.OPEN_ACCOUNT;
            case 2 -> MenuOption.DEPOSIT;
            case 3 -> MenuOption.WITHDRAW;
            case 4 -> MenuOption.TRANSFER;
            case 5 -> MenuOption.STATEMENT;
            case 6 -> MenuOption.LIST_ACCOUNTS;
            case 7 -> MenuOption.BATCH;
            case 8 -> MenuOption.QUEUED_BATCH;
            case 9 -> MenuOption.REPORT;
            case 10 -> MenuOption.INTEREST;
            case 11 -> MenuOption.EXIT;
            default -> null;
        };
    }

    private static void openAccount(Scanner input, BankService bank)
            throws BankException, IOException {
        String name = readText(input, "Customer name: ");
        String email = readText(input, "Email: ");
        String mobile = readText(input, "Mobile number: ");
        String pan = readText(input, "PAN (press Enter to skip): ");
        String addressLine = readText(input, "Address line (press Enter to skip): ");
        String city = readText(input, "City (press Enter to skip): ");
        String pincode = readText(input, "Pincode (press Enter to skip): ");
        AccountType type = readAccountType(input);
        long openingBalance = readLong(input, "Opening balance in rupees: ");
        long minimumBalance = 0;
        long overdraftLimit = 0;
        if (type == AccountType.SAVINGS || type == AccountType.PREMIUM) {
            minimumBalance = readLong(input, "Minimum balance in rupees: ");
        } else if (type == AccountType.CURRENT) {
            overdraftLimit = readLong(input, "Overdraft limit in rupees: ");
        }
        Account account = bank.openAccount(name, email, mobile, pan,
                new Customer.Address(addressLine, city, pincode), type,
                openingBalance, minimumBalance, overdraftLimit);
        System.out.println("Account opened: " + account);
    }

    private static AccountType readAccountType(Scanner input) {
        while (true) {
            System.out.println("Account type: SAVINGS, CURRENT, FIXED_DEPOSIT, PREMIUM");
            String value = readText(input, "Type: ");
            try {
                return AccountType.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                System.out.println("Please enter one of the listed account types.");
            }
        }
    }

    private static void deposit(Scanner input, BankService bank)
            throws BankException, IOException {
        String accountNumber = readText(input, "Account number: ");
        long amount = readLong(input, "Deposit amount: ");
        bank.deposit(accountNumber, amount);
        System.out.println("Deposit completed.");
    }

    private static void withdraw(Scanner input, BankService bank)
            throws BankException, IOException {
        String accountNumber = readText(input, "Account number: ");
        long amount = readLong(input, "Withdrawal amount: ");
        bank.withdraw(accountNumber, amount);
        System.out.println("Withdrawal completed.");
    }

    private static void transfer(Scanner input, BankService bank)
            throws BankException, IOException {
        String from = readText(input, "From account: ");
        String to = readText(input, "To account: ");
        long amount = readLong(input, "Transfer amount: ");
        bank.transfer(from, to, amount);
        System.out.println("Transfer completed.");
    }

    private static void statement(Scanner input, BankService bank)
            throws BankException {
        String accountNumber = readText(input, "Account number: ");
        System.out.println(bank.statement(accountNumber));
    }

    private static void listAccounts(BankService bank) {
        List<Account> accounts = bank.listAccounts();
        if (accounts.isEmpty()) {
            System.out.println("There are no accounts yet.");
            return;
        }
        for (Account account : accounts) {
            System.out.println(account + " | yearly interest: "
                    + formatCurrency(account.yearlyInterest()));
        }
        System.out.println("Average balance: " + formatCurrency(bank.averageBalance()));
    }

    private static void processBatch(Scanner input, BankService bank, boolean queued)
            throws BankException {
        System.out.println("Enter transaction commands. Submit an empty line when done.");
        List<String> commands = new ArrayList<>();
        while (true) {
            String command = input.nextLine();
            if (command.isBlank()) {
                break;
            }
            commands.add(command);
        }
        List<String> results = queued
                ? bank.processQueuedBatch(commands)
                : bank.processBatch(commands);
        for (String result : results) {
            System.out.println(result);
        }
    }

    private static void createReport(BankService bank) throws IOException {
        System.out.println(bank.createReport(REPORT_FILE));
        System.out.println("Report saved to " + REPORT_FILE);
    }

    private static void showInterest(Scanner input, BankService bank) {
        String accountNumber = readText(input, "Account number: ");
        try {
            System.out.println("Yearly interest: "
                    + formatCurrency(bank.yearlyInterest(accountNumber)));
        } catch (BankException exception) {
            System.out.println("Operation failed: " + exception.getMessage());
        }
    }

    private static String readText(Scanner input, String prompt) {
        System.out.print(prompt);
        return input.nextLine().trim();
    }

    private static int readInteger(Scanner input, String prompt) {
        while (true) {
            String value = readText(input, prompt);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private static long readLong(Scanner input, String prompt) {
        while (true) {
            String value = readText(input, prompt);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a whole number.");
            }
        }
    }
}
