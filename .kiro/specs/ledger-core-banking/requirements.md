# Requirements Document

## Introduction

Ledger-Core-Banking is a production-grade core banking backend that manages users, accounts, and money movement with strict correctness guarantees. The system implements double-entry bookkeeping so that every financial transaction is balanced, enforces ACID-compliant persistence on PostgreSQL, and uses database row-level locking to handle concurrent withdrawals from the same account without losing or duplicating funds. Access is secured with JWT-based authentication, OAuth2 authorization flows, and Role-Based Access Control (RBAC). A polished web dashboard demonstrates the engine by allowing authorized users to manage accounts, initiate transfers, and inspect the immutable ledger.

The defining correctness goal of this system is monetary integrity: the sum of all ledger entries for any transaction is always zero, account balances are always derivable from posted ledger entries, and no concurrent operation can cause a balance to become inconsistent or negative beyond allowed limits.

## Glossary

- **System**: The Ledger-Core-Banking backend application as a whole.
- **API**: The HTTP interface exposed by the System for clients (including the Dashboard).
- **Auth_Service**: The component responsible for authenticating users and issuing/validating tokens.
- **Authz_Service**: The component responsible for evaluating Role-Based Access Control permissions.
- **Account_Service**: The component responsible for account lifecycle and balance queries.
- **Ledger_Service**: The component responsible for recording double-entry transactions and computing balances.
- **Transfer_Service**: The component responsible for orchestrating money movement between accounts.
- **Audit_Service**: The component responsible for recording an immutable, append-only audit trail.
- **Dashboard**: The web frontend client that consumes the API.
- **User**: A person with credentials who can authenticate to the System.
- **Role**: A named set of permissions assigned to a User. Defined roles are CUSTOMER, TELLER, and ADMIN.
- **Account**: A record holding a monetary balance in a single Currency, owned by a User.
- **Ledger_Entry**: A single line of a Transaction recording either a debit or a credit against one Account for a specific Amount.
- **Transaction**: A balanced group of two or more Ledger_Entries representing one financial event. Also referred to as a journal entry.
- **Debit**: A Ledger_Entry that increases an asset account balance or decreases a liability account balance, recorded as a positive movement on the debit side.
- **Credit**: A Ledger_Entry that decreases an asset account balance or increases a liability account balance, recorded on the credit side.
- **Transfer**: An operation that moves a specified Amount from a source Account to a destination Account, recorded as one balanced Transaction.
- **Amount**: A non-negative monetary value represented with fixed decimal precision (no floating point) and an associated Currency.
- **Currency**: An ISO 4217 three-letter currency code (for example, USD).
- **Available_Balance**: The Account balance available for withdrawal, computed from posted Ledger_Entries.
- **Idempotency_Key**: A client-supplied unique identifier used to ensure a money-movement request is processed at most once.
- **JWT**: A signed JSON Web Token issued by the Auth_Service and presented by clients to authenticate API requests.
- **Access_Token**: A short-lived JWT used to authorize API requests.
- **Refresh_Token**: A longer-lived credential used to obtain a new Access_Token without re-entering credentials.
- **Row_Lock**: A PostgreSQL row-level lock acquired on an Account record to serialize concurrent balance-changing operations.
- **Overdraft_Limit**: The maximum amount by which an Account's Available_Balance is permitted to go below zero. Defaults to zero.

## Requirements

### Requirement 1: User Registration and Identity

**User Story:** As a prospective customer, I want to register an account with credentials, so that I can securely access the banking system.

#### Acceptance Criteria

1. WHEN a registration request is received with a unique, well-formed email and a password meeting the password policy, THE Auth_Service SHALL create exactly one User record with the CUSTOMER role and return a success response containing the created User's unique identifier and assigned CUSTOMER role.
2. IF a registration request is received with an email that, compared case-insensitively, already belongs to an existing User, THEN THE Auth_Service SHALL reject the request, create no User record, and return a conflict error.
3. IF a registration request is received with a password that does not meet the password policy, THEN THE Auth_Service SHALL reject the request, create no User record, and return a validation error identifying each unmet policy criterion.
4. THE Auth_Service SHALL store User passwords only as salted cryptographic hashes and SHALL NOT store passwords in plaintext or any reversible form.
5. WHERE password policy is enforced, THE Auth_Service SHALL require a password of at least 12 characters and at most 128 characters containing at least one letter and at least one digit.
6. IF a registration request is received with an email that is not well-formed (lacking a single "@" separator with a non-empty local part and a non-empty domain part containing at least one "."), THEN THE Auth_Service SHALL reject the request, create no User record, and return a validation error identifying the email field.

### Requirement 2: Authentication and Token Issuance

**User Story:** As a registered user, I want to authenticate and receive tokens, so that I can make authorized API requests.

#### Acceptance Criteria

1. WHEN a login request is received with credentials matching a stored User, THE Auth_Service SHALL issue an Access_Token and a Refresh_Token.
2. IF a login request is received with credentials that do not match any stored User, THEN THE Auth_Service SHALL reject the request and return an authentication error that is identical regardless of whether the email exists, so that it does not reveal whether the email exists.
3. THE Auth_Service SHALL set each Access_Token to expire 15 minutes after issuance.
4. WHEN a token refresh request is received with a Refresh_Token that is unexpired and has not been previously invalidated, THE Auth_Service SHALL issue a new Access_Token.
5. IF an API request is received with an Access_Token that is expired, malformed, or fails signature verification, THEN THE API SHALL reject the request, perform no requested action, and return an authentication error.
6. WHEN a User submits a logout request with a valid Refresh_Token, THE Auth_Service SHALL invalidate that Refresh_Token so that it can no longer be used to obtain an Access_Token.
7. THE Auth_Service SHALL set each Refresh_Token to expire 7 days after issuance.
8. IF a token refresh request is received with a Refresh_Token that is expired, malformed, fails signature verification, or has been previously invalidated, THEN THE Auth_Service SHALL reject the request, issue no Access_Token, and return an authentication error.
9. IF 5 consecutive failed login attempts are received for the same email within a 15-minute window, THEN THE Auth_Service SHALL reject further login attempts for that email for 15 minutes and return an authentication error.

### Requirement 3: Role-Based Access Control

**User Story:** As a bank operator, I want access governed by roles, so that users can only perform actions appropriate to their role.

#### Acceptance Criteria

1. THE Authz_Service SHALL maintain for every User exactly one Role from the set {CUSTOMER, TELLER, ADMIN}.
2. IF a request is received to access or modify an Account, AND the requesting User holds the CUSTOMER Role and does not own that Account, THEN THE Authz_Service SHALL deny the request, leave the Account unchanged, and return an authorization error indicating insufficient permission.
3. WHERE the requesting User holds the TELLER Role, THE Authz_Service SHALL permit read access to any Account.
4. WHERE the requesting User holds the ADMIN Role, THE Authz_Service SHALL permit User Role management.
5. IF a request is received to perform an action for which the requesting User's Role lacks permission, THEN THE Authz_Service SHALL deny the request, leave the affected Account and ledger records unchanged, and return an authorization error indicating insufficient permission.
6. WHEN an ADMIN submits a request to change an existing User's Role to a value in the set {CUSTOMER, TELLER, ADMIN}, THE Authz_Service SHALL update that User's Role and record the change, including the previous Role, the new Role, the acting ADMIN, and a timestamp, in the audit trail.
7. WHERE the requesting User holds the TELLER Role, THE Authz_Service SHALL permit posting of Transfers on behalf of customers.
8. WHERE the requesting User holds the ADMIN Role, THE Authz_Service SHALL permit read access to all Accounts and ledger records.
9. IF an ADMIN submits a request to change a User's Role to a value not in the set {CUSTOMER, TELLER, ADMIN}, OR the target User does not exist, THEN THE Authz_Service SHALL deny the request, leave the target User's Role unchanged, and return an error indicating the request is invalid.

### Requirement 4: Account Management

**User Story:** As a customer, I want to open and view accounts, so that I can hold and track my money.

#### Acceptance Criteria

1. WHEN an authorized request to open an Account is received specifying a Currency that is a supported ISO 4217 three-letter code, THE Account_Service SHALL create an Account with a zero Available_Balance, a unique account identifier, and an initial status of ACTIVE.
2. THE Account_Service SHALL assign each Account exactly one Currency at creation that remains unchanged for the lifetime of the Account.
3. WHEN an authorized request to view an existing Account is received, THE Account_Service SHALL return the Account identifier, owner, Currency, Available_Balance, and status, where status is one of {ACTIVE, CLOSED}.
4. THE Account_Service SHALL compute an Account's Available_Balance solely from that Account's posted Ledger_Entries.
5. WHEN an authorized request to close an ACTIVE Account is received AND the Account's Available_Balance is zero, THE Account_Service SHALL set the Account status to CLOSED.
6. IF a request to close an Account is received AND the Account's Available_Balance is non-zero, THEN THE Account_Service SHALL reject the request, leave the Account status unchanged, and return a validation error.
7. IF a money-movement request targets an Account whose status is CLOSED, THEN THE Account_Service SHALL reject the request, leave the Account unchanged, and return a validation error.
8. IF a request to open an Account specifies a Currency that is not a supported ISO 4217 three-letter code, THEN THE Account_Service SHALL reject the request, create no Account, and return a validation error identifying the Currency field.
9. IF a request to view or close an Account references an Account that does not exist, THEN THE Account_Service SHALL reject the request and return a not-found error.
10. IF a request to close an Account is received AND the Account status is already CLOSED, THEN THE Account_Service SHALL reject the request, leave the Account status unchanged, and return a validation error.

### Requirement 5: Double-Entry Bookkeeping Integrity

**User Story:** As a financial controller, I want every transaction to be balanced, so that the books always reconcile.

#### Acceptance Criteria

1. THE Ledger_Service SHALL record each Transaction as at least two Ledger_Entries comprising at least one Debit and at least one Credit.
2. FOR ALL Transactions, THE Ledger_Service SHALL ensure that the sum of debit Amounts equals the sum of credit Amounts, so that the signed sum of all Ledger_Entries in the Transaction is zero.
3. IF a request to post a Transaction is received whose sum of debit Amounts does not equal its sum of credit Amounts, THEN THE Ledger_Service SHALL reject the Transaction, post no Ledger_Entries, and return a validation error indicating the debit/credit imbalance.
4. THE Ledger_Service SHALL require all Ledger_Entries within a single Transaction to share the same Currency.
5. IF a request to post a Transaction is received whose Ledger_Entries do not all share the same Currency, THEN THE Ledger_Service SHALL reject the Transaction, post no Ledger_Entries, and return a validation error indicating the Currency mismatch.
6. IF a request to post a Transaction is received that does not contain at least one Debit and at least one Credit, THEN THE Ledger_Service SHALL reject the Transaction, post no Ledger_Entries, and return a validation error indicating the missing Debit or Credit.
7. THE Ledger_Service SHALL persist each posted Ledger_Entry as an append-only record that is never updated or deleted after posting.
8. WHEN a correction to a posted Transaction is required, THE Ledger_Service SHALL record a new reversing Transaction whose Ledger_Entries are equal in Amount and opposite in direction to the original Transaction's Ledger_Entries and that references the original Transaction's identifier, without modifying or deleting the original Ledger_Entries.
9. THE Ledger_Service SHALL represent every Amount using fixed-precision decimal values and SHALL NOT use binary floating-point representation for monetary values.

### Requirement 6: Funds Transfer

**User Story:** As a customer, I want to transfer money to another account, so that I can pay others.

#### Acceptance Criteria

1. WHEN an authorized Transfer request is received with an existing source Account, an existing destination Account, and a positive Amount, THE Transfer_Service SHALL post one balanced Transaction debiting the source Account and crediting the destination Account for that Amount and SHALL return the posted Transaction identifier as the success result.
2. IF a Transfer request specifies an Amount that is zero or negative, THEN THE Transfer_Service SHALL reject the request and return a validation error.
3. IF a Transfer request specifies a source Account and destination Account with different Currencies, THEN THE Transfer_Service SHALL reject the request and return a validation error.
4. IF a Transfer request would reduce the source Account's Available_Balance below the negative of its Overdraft_Limit, THEN THE Transfer_Service SHALL reject the request and return an insufficient-funds error.
5. IF a Transfer request specifies a source Account identical to the destination Account, THEN THE Transfer_Service SHALL reject the request and return a validation error.
6. WHEN a Transfer is posted successfully, THE Transfer_Service SHALL update both Accounts' Available_Balances atomically so that either both Ledger_Entries are persisted or neither is persisted.
7. IF a Transfer request specifies a source Account or a destination Account that does not exist, THEN THE Transfer_Service SHALL reject the request and return a not-found error.
8. IF a Transfer request is rejected for any reason, THEN THE Transfer_Service SHALL post no Ledger_Entries and SHALL leave both Accounts' Available_Balances unchanged.

### Requirement 7: Concurrency-Safe Withdrawals and Balance Changes

**User Story:** As a bank, I want concurrent withdrawals on the same account handled safely, so that no money is lost or created when requests arrive at the same time.

#### Acceptance Criteria

1. WHEN two or more balance-changing requests target the same Account concurrently, THE Transfer_Service SHALL acquire a Row_Lock on that Account before reading its Available_Balance and SHALL hold the Row_Lock until the Transaction commits or aborts.
2. WHILE a Row_Lock on an Account is held by one Transaction, THE Transfer_Service SHALL cause any other balance-changing Transaction on that Account to wait until the Row_Lock is released, processing waiting Transactions in the order their Row_Lock requests were received.
3. FOR ALL sequences of concurrent balance-changing requests on a single Account, THE Ledger_Service SHALL produce a final Available_Balance equal to the result of applying those Transactions in some serial order, such that the sum of all committed debits and credits equals the net change in Available_Balance with zero discrepancy.
4. IF two or more concurrent withdrawal requests target the same Account AND only a subset can succeed without reducing Available_Balance below the negative of the Overdraft_Limit, THEN THE Transfer_Service SHALL commit only those withdrawals that keep Available_Balance at or above the negative of the Overdraft_Limit and SHALL reject each remaining withdrawal with an error indicating insufficient funds.
5. WHEN a rejected withdrawal occurs, THE Transfer_Service SHALL leave the Account's Available_Balance unchanged by that rejected Transaction and SHALL not persist any partial balance change for it.
6. WHEN multiple Accounts must be locked within a single Transaction, THE Transfer_Service SHALL acquire Row_Locks one at a time in ascending order of Account identifier to prevent deadlock.
7. IF a balance-changing Transaction cannot acquire all of its required Row_Locks within the configured lock-wait timeout, which SHALL default to 5 seconds and SHALL be configurable within the range of 1 second to 30 seconds, THEN THE Transfer_Service SHALL abort the Transaction, roll back any uncommitted balance changes so that all affected Available_Balance values are restored to their pre-Transaction values, and return an error indicating the operation is retryable.

### Requirement 8: Idempotent Money Movement

**User Story:** As a client developer, I want money-movement requests to be idempotent, so that retries after a network failure do not move money twice.

#### Acceptance Criteria

1. WHEN a Transfer request is received with a well-formed Idempotency_Key (a string of 1 to 128 characters) that has not been previously recorded, THE Transfer_Service SHALL process the Transfer and persist the resulting Transaction identifier together with the request parameters (source Account, destination Account, and Amount) against that Idempotency_Key.
2. WHEN a Transfer request is received with an Idempotency_Key that matches a previously completed request AND whose request parameters (source Account, destination Account, and Amount) are identical to those of the original request, THE Transfer_Service SHALL return the original Transaction result and SHALL NOT post a second Transaction.
3. IF a Transfer request is received with an Idempotency_Key that matches an in-progress request, THEN THE Transfer_Service SHALL reject the duplicate request, return a conflict error, and SHALL NOT post a Transaction.
4. IF a Transfer request is received with an Idempotency_Key that matches a previously recorded request AND whose request parameters (source Account, destination Account, or Amount) differ from those of the recorded request, THEN THE Transfer_Service SHALL reject the request, return a conflict error, and SHALL NOT post a Transaction.
5. IF a money-movement request is received without an Idempotency_Key, or with an Idempotency_Key that is not a string of 1 to 128 characters, THEN THE Transfer_Service SHALL reject the request and return a validation error.
6. THE Transfer_Service SHALL retain each recorded Idempotency_Key and its associated Transaction result for at least 24 hours after the corresponding request completes.

### Requirement 9: ACID Persistence Guarantees

**User Story:** As a financial controller, I want all persistence to be ACID-compliant, so that the ledger is never left in a partial or corrupt state.

#### Acceptance Criteria

1. THE Ledger_Service SHALL persist all Ledger_Entries of a single Transaction within one database transaction so that either all entries commit or none commit.
2. IF any database error occurs while posting a Transaction, THEN THE Ledger_Service SHALL roll back the entire database transaction, post no Ledger_Entries, leave the Available_Balance of every affected Account unchanged, and return an error response indicating that the Transaction was not persisted.
3. WHILE executing a balance-changing operation, THE System SHALL apply an isolation level that prevents dirty reads, non-repeatable reads, and lost updates.
4. WHEN a Transaction commits, THE Ledger_Service SHALL persist all of its Ledger_Entries to non-volatile storage before returning a success response, such that the committed Ledger_Entries remain present and unchanged after a subsequent System restart.
5. IF the System restarts or the posting process terminates before a Transaction's database transaction has committed, THEN THE Ledger_Service SHALL persist none of that Transaction's Ledger_Entries and SHALL leave the Available_Balance of every affected Account unchanged.

### Requirement 10: Ledger Query and Statements

**User Story:** As a customer, I want to view my transaction history, so that I can reconcile my account.

#### Acceptance Criteria

1. WHEN an authorized request for an Account's Ledger_Entries is received, THE Ledger_Service SHALL return the Account's Ledger_Entries ordered by posting time in ascending order, with Ledger_Entries sharing an identical posting time ordered by their unique identifier in ascending order.
2. WHERE a date range is supplied with a Ledger query, THE Ledger_Service SHALL return only Ledger_Entries with a posting time on or after the start date and on or before the end date.
3. WHEN an authorized request for an Account statement is received, THE Ledger_Service SHALL return a running Available_Balance after each Ledger_Entry that equals the sum of all prior posted Ledger_Entries for that Account, inclusive of the current Ledger_Entry.
4. WHERE pagination parameters are supplied with a Ledger query, THE Ledger_Service SHALL return at most the requested page size, where the page size is constrained to a minimum of 1 and a maximum of 500 Ledger_Entries and defaults to 50 Ledger_Entries when not specified, and SHALL return a cursor for retrieving subsequent results.
5. IF a request for an Account's Ledger_Entries or Account statement is not authorized, THEN THE Ledger_Service SHALL reject the request, return no Ledger_Entry data, and return an error response indicating authorization failure.
6. IF a date range is supplied where the start date is later than the end date, THEN THE Ledger_Service SHALL reject the query, return no Ledger_Entries, and return an error response indicating the date range is invalid.
7. IF a pagination cursor that is invalid or no longer valid is supplied, THEN THE Ledger_Service SHALL reject the query, return no Ledger_Entries, and return an error response indicating the cursor is invalid.

### Requirement 11: Audit Trail

**User Story:** As a compliance officer, I want an immutable audit trail, so that I can investigate every sensitive action.

#### Acceptance Criteria

1. WHEN a User authentication attempt succeeds or fails, an Account is opened or closed, a Transfer is posted, or a User's Role is changed, THE Audit_Service SHALL record an audit entry containing the actor identity, the action type, the target identifier, the outcome (success or failure), and a timestamp recorded with at least millisecond precision.
2. THE Audit_Service SHALL persist audit entries as append-only records that are never updated or deleted.
3. WHEN an authorized ADMIN explicitly requests audit entries, THE Audit_Service SHALL return the matching audit entries ordered by timestamp in ascending order, with entries sharing an identical timestamp ordered by a deterministic secondary key so that the ordering is repeatable, and SHALL return audit entries only in response to such an authorized request.
4. IF a request to modify or delete an existing audit entry is received, THEN THE Audit_Service SHALL deny the request, leave the existing audit entry unchanged, and return an authorization error.
5. IF the Audit_Service cannot retrieve or order the requested audit entries due to an internal error, THEN THE Audit_Service SHALL return an error response and SHALL NOT return a partial or unordered result set.
6. IF the Audit_Service cannot durably persist an audit entry for a sensitive action, THEN THE Audit_Service SHALL return a failure indication for that recording, and the corresponding sensitive action SHALL NOT be reported as successful.

### Requirement 12: Input Validation and Error Reporting

**User Story:** As a client developer, I want consistent validation and error responses, so that I can handle failures predictably.

#### Acceptance Criteria

1. IF an API request is received with a malformed body or one or more missing required fields, THEN THE API SHALL reject the request, make no change to System state, and return a validation error that identifies each invalid or missing field.
2. THE API SHALL represent monetary Amounts in requests and responses as fixed-precision decimal strings, each accompanied by an explicit ISO 4217 Currency code.
3. IF an API request specifies an Amount with more decimal places than the requested Currency's minor-unit precision, THEN THE API SHALL reject the request, make no change to System state, and return a validation error identifying the Amount field.
4. WHEN the API returns an error response, THE API SHALL include a machine-readable error code drawn from a defined set of error codes and a human-readable message describing the error.
5. WHEN a request completes successfully, THE API SHALL return a success response that contains no error code and no error message.
6. IF an API request specifies a Currency that is not a valid ISO 4217 three-letter currency code, THEN THE API SHALL reject the request, make no change to System state, and return a validation error identifying the Currency field.
7. IF an API request specifies an Amount that is not a valid non-negative decimal string, THEN THE API SHALL reject the request, make no change to System state, and return a validation error identifying the Amount field.

### Requirement 13: Dashboard Authentication and Session

**User Story:** As a user, I want to sign in to the dashboard, so that I can manage my banking securely.

#### Acceptance Criteria

1. WHEN a User submits valid credentials on the Dashboard login screen, THE Dashboard SHALL obtain an Access_Token and a Refresh_Token from the Auth_Service, store both tokens for use on subsequent API requests, and transition from the login screen to the authenticated dashboard view.
2. IF a User submits credentials on the Dashboard login screen that the Auth_Service rejects, THEN THE Dashboard SHALL remain on the login screen, SHALL NOT establish an authenticated session, and SHALL display a single generic authentication error message that does not indicate whether the email exists or whether the password was incorrect.
3. WHILE a stored Access_Token is within its validity period, THE Dashboard SHALL include that Access_Token on every API request it sends to the API.
4. WHEN an Access_Token expires during an active session AND a valid Refresh_Token is stored, THE Dashboard SHALL use the Refresh_Token to obtain a new Access_Token from the Auth_Service and retry the original API request without prompting the User to re-enter credentials.
5. IF the Dashboard requests a new Access_Token AND the Auth_Service rejects the Refresh_Token as expired or invalid, THEN THE Dashboard SHALL terminate the active session, discard all stored tokens, and return the User to the login screen to re-authenticate.
6. WHEN a User selects logout on the Dashboard, THE Dashboard SHALL request the Auth_Service to invalidate the Refresh_Token, discard all stored tokens so that no token is included on subsequent API requests, and return the User to the login screen.

### Requirement 14: Dashboard Account and Transfer Experience

**User Story:** As a customer, I want a polished dashboard, so that I can view balances and move money intuitively.

#### Acceptance Criteria

1. WHEN an authenticated User opens the Dashboard home view, THE Dashboard SHALL display each owned Account with its Currency and current Available_Balance within 3 seconds of the view being requested.
2. WHEN a User submits a Transfer through the Dashboard, THE Dashboard SHALL generate an Idempotency_Key for the request and submit it to the Transfer_Service.
3. WHILE a Transfer request is in progress, THE Dashboard SHALL disable the submit control to prevent duplicate submissions.
4. WHEN a Transfer request completes with either success or failure, THE Dashboard SHALL re-enable the submit control so the User can take a subsequent action.
5. WHEN a Transfer succeeds, THE Dashboard SHALL display a confirmation and the updated Available_Balances of the affected Accounts.
6. IF a Transfer is rejected with an insufficient-funds error, THEN THE Dashboard SHALL display a message indicating that the Available_Balance is insufficient for the requested amount, re-enable the submit control, and leave the Available_Balances of all affected Accounts unchanged.
7. IF a Transfer request does not receive a response from the Transfer_Service within 30 seconds, THEN THE Dashboard SHALL display a message indicating the request timed out, re-enable the submit control, and leave the Available_Balances of all affected Accounts unchanged.
8. WHEN an authenticated User opens an Account's history view, THE Dashboard SHALL display that Account's Ledger_Entries with a running balance, showing at most 50 Ledger_Entries per page.
9. THE Dashboard SHALL render all monetary values using the symbol and decimal precision defined by the Account's Currency.
10. WHEN an authenticated User opens the Dashboard home view and owns zero Accounts, THE Dashboard SHALL display an empty-state message indicating that no Accounts exist.
11. WHERE the viewport width is below 768 pixels, THE Dashboard SHALL present a responsive layout that preserves access to balances, transfers, and history.
