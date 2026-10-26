# MiniBank

MiniBank is a small console banking project that brings the MiniBank Part B
work from Practicals 1–14 together. It uses Java 17 and keeps the code in
separate `model`, `service`, `repository`, `util`, `exception`, and `annotation`
packages.

## Build and run

Install JDK 17 or later. On Windows, run:

```text
build.bat
java -jar MiniBank.jar
```

Or use Maven:

```text
mvn test
mvn package
java -jar target\minibank-1.0.jar
```

The first run starts with no accounts. Choose **Open account** and follow the
prompts. The menu lets you deposit, withdraw, transfer, view statements, list
accounts, process a batch of commands, create a transaction report, and view
yearly interest. Choose **Exit** to save accounts.

## Batch commands

Enter one command per line, then submit an empty line to process the batch:

```text
DEPOSIT AC0001 500
WITHDRAW AC0001 100
TRANSFER AC0001 AC0002 50
```

Each command is validated and run on a fixed thread pool. Individual command
errors are reported without stopping the rest of the batch.

## Project features

- Practical 1: menu, `BankInfo` record, and menu enum.
- Practical 2: encapsulated customer/account data and generated IDs.
- Practical 3: account equality, hashing, readable output, and customer address cloning.
- Practical 4: contact validators, command parsing, and statements.
- Practical 5: abstract accounts and savings/current/fixed-deposit subclasses.
- Practical 6: interfaces, lambdas, package structure, and runnable JAR.
- Practicals 7–8: runtime annotations, reflection, and checked bank exceptions.
- Practicals 9–10: synchronized balances, thread-pool batches, producer-consumer
  coordination, and consistent transfer lock ordering.
- Practical 11: serialization, transaction logs, and NIO reports.
- Practicals 12–13: collection-based storage/sorting and a generic `@Id` repository.
- Practical 14: integrated service/menu, persistence, batch processing, and JUnit tests.

Runtime files are created under `data\` and are excluded from Git.

The account list is sorted by balance and also shows the average balance,
which uses a bounded generic method (`<N extends Number>`).

The `@Positive` annotation is applied to transaction amounts rather than the
account balance, because current accounts may validly use their overdraft and
savings accounts may be opened with a zero balance.

## Submit on GitHub

Create a new GitHub repository for this project, open a terminal in this
`minibank` folder, and run:

```text
git init
git add .
git commit -m "Add integrated MiniBank practical project"
git branch -M main
git remote add origin <your-GitHub-repository-URL>
git push -u origin main
```
